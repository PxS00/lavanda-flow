import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { Observable, Subject } from 'rxjs';

import { RegisterSupplierRequest, SupplierDto } from '../../data-access/supplier.dto';
import { SupplierApiService } from '../../data-access/supplier-api.service';
import { SupplierRegistrationPage } from './supplier-registration-page';

describe('SupplierRegistrationPage', () => {
  const registeredSupplier: SupplierDto = {
    id: '53b1fdb4-72ab-41bb-b9e7-381d922d69a8',
    name: 'Lavanda Supplies',
    identifier: null,
    contact: null,
    notes: null,
    active: true,
  };

  let fixture: ComponentFixture<SupplierRegistrationPage>;
  let response: Subject<SupplierDto>;
  let register: ReturnType<
    typeof vi.fn<(request: RegisterSupplierRequest) => Observable<SupplierDto>>
  >;
  let router: Router;

  beforeEach(async () => {
    response = new Subject<SupplierDto>();
    register = vi.fn(() => response);

    await TestBed.configureTestingModule({
      imports: [SupplierRegistrationPage],
      providers: [provideRouter([]), { provide: SupplierApiService, useValue: { register } }],
    }).compileComponents();

    router = TestBed.inject(Router);
    fixture = TestBed.createComponent(SupplierRegistrationPage);
    fixture.detectChanges();
  });

  it('should show required validation and avoid submitting an invalid form', () => {
    submitForm();

    expect(fixture.nativeElement.textContent).toContain('Name is required.');
    expect(register).not.toHaveBeenCalled();
  });

  it('should enforce backend-aligned maximum lengths', () => {
    setValidModel({ identifier: 'x'.repeat(256) });

    submitForm();

    expect(fixture.nativeElement.textContent).toContain(
      'Identifier must be 255 characters or fewer.',
    );
    expect(register).not.toHaveBeenCalled();
  });

  it('should send the exact request and normalize blank optional fields to null', () => {
    setValidModel({ identifier: '   ', contact: '', notes: '  ' });

    submitForm();

    expect(register).toHaveBeenCalledWith({
      name: 'Lavanda Supplies',
      identifier: null,
      contact: null,
      notes: null,
    });
  });

  it('should navigate to the stable detail route after registration', () => {
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    setValidModel();
    submitForm();

    response.next(registeredSupplier);

    expect(navigate).toHaveBeenCalledWith(['/suppliers', registeredSupplier.id]);
  });

  it('should associate mapped backend field errors with the relevant control', () => {
    setValidModel();
    submitForm();

    response.error(
      new HttpErrorResponse({
        status: 400,
        error: {
          timestamp: '2026-09-01T12:00:00Z',
          status: 400,
          error: 'Bad Request',
          code: 'VALIDATION_ERROR',
          message: 'Request validation failed.',
          path: '/api/v1/suppliers',
          details: { name: 'must not be blank' },
        },
      }),
    );
    fixture.detectChanges();

    const nameInput = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    const nameError = fixture.nativeElement.querySelector('#supplier-name-backend-error') as Element | null;

    expect(nameInput.getAttribute('aria-describedby')).toContain('supplier-name-backend-error');
    expect(nameError?.textContent).toContain('must not be blank');
  });

  it('should prevent duplicate submissions while the request is pending', () => {
    setValidModel();

    submitForm();
    submitForm();

    expect(register).toHaveBeenCalledTimes(1);
    expect((findSubmitButton() as HTMLButtonElement).disabled).toBe(true);
  });

  function setValidModel(
    overrides: Partial<ReturnType<typeof fixture.componentInstance.registrationModel>> = {},
  ): void {
    fixture.componentInstance.registrationModel.set({
      name: 'Lavanda Supplies',
      identifier: '12.345.678/0001-90',
      contact: 'suppliers@example.test',
      notes: 'Preferred supplier',
      ...overrides,
    });
    fixture.detectChanges();
  }

  function submitForm(): void {
    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new SubmitEvent('submit', { bubbles: true, cancelable: true }));
    fixture.detectChanges();
  }

  function findSubmitButton(): Element {
    const button = fixture.nativeElement.querySelector('button[type="submit"]') as Element | null;
    expect(button).not.toBeNull();
    return button as Element;
  }
});
