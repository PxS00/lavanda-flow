import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { Observable, Subject } from 'rxjs';

import {
  InventoryItemDto,
  RegisterInventoryItemRequest,
} from '../../data-access/inventory-item.dto';
import { InventoryItemApiService } from '../../data-access/inventory-item-api.service';
import { InventoryItemRegistrationPage } from './inventory-item-registration-page';

describe('InventoryItemRegistrationPage', () => {
  const registeredItem: InventoryItemDto = {
    id: 'bd194732-51cf-4f73-bc5d-3a9f9337adcc',
    name: 'Lavender Essence',
    description: null,
    category: 'ESSENCE',
    unitOfMeasure: 'MILLILITER',
    active: true,
    essenceReference: null,
    productionTypeCode: null,
  };

  let fixture: ComponentFixture<InventoryItemRegistrationPage>;
  let response: Subject<InventoryItemDto>;
  let register: ReturnType<
    typeof vi.fn<(request: RegisterInventoryItemRequest) => Observable<InventoryItemDto>>
  >;
  let router: Router;

  beforeEach(async () => {
    response = new Subject<InventoryItemDto>();
    register = vi.fn(() => response);

    await TestBed.configureTestingModule({
      imports: [InventoryItemRegistrationPage],
      providers: [provideRouter([]), { provide: InventoryItemApiService, useValue: { register } }],
    }).compileComponents();

    router = TestBed.inject(Router);
    fixture = TestBed.createComponent(InventoryItemRegistrationPage);
    fixture.detectChanges();
  });

  it('should show required validation and avoid submitting an invalid form', () => {
    submitForm();

    expect(fixture.nativeElement.textContent).toContain('Nome é obrigatório.');
    expect(fixture.nativeElement.textContent).toContain('Categoria é obrigatória.');
    expect(fixture.nativeElement.textContent).toContain('Unidade de medida é obrigatória.');
    expect(register).not.toHaveBeenCalled();
  });

  it('should show max-length validation', () => {
    setValidModel({ name: 'x'.repeat(256) });

    submitForm();

    expect(fixture.nativeElement.textContent).toContain('Nome deve ter no máximo 255 caracteres.');
    expect(register).not.toHaveBeenCalled();
  });

  it('should send the exact request, including optional production metadata', () => {
    setValidModel({ description: '   ', essenceReference: '027', productionTypeCode: 'BDS' });

    submitForm();

    expect(register).toHaveBeenCalledWith({
      name: 'Lavender Essence',
      description: null,
      category: 'ESSENCE',
      unitOfMeasure: 'MILLILITER',
      essenceReference: '027',
      productionTypeCode: 'BDS',
    });
  });

  it('should navigate to the stable detail route after registration', () => {
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    setValidModel();
    submitForm();

    response.next(registeredItem);

    expect(navigate).toHaveBeenCalledWith(['/catalog', registeredItem.id]);
  });

  it('should render mapped backend validation errors', () => {
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
          path: '/api/v1/inventory-items',
          details: { name: 'must not be blank' },
        },
      }),
    );
    fixture.detectChanges();

    const nameInput = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    const nameError = fixture.nativeElement.querySelector('#name-backend-error') as Element | null;

    expect(nameInput.getAttribute('aria-describedby')).toContain('name-backend-error');
    expect(nameError?.textContent).toContain('Verifique o valor informado.');
    expect(fixture.nativeElement.textContent).not.toContain('Ocorreu um problema');
  });

  it('should retain global validation errors that do not identify a field', () => {
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
          path: '/api/v1/inventory-items',
          details: {},
        },
      }),
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Revise os dados informados.');
    expect(fixture.nativeElement.textContent).toContain('Ocorreu um problema');
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
      name: 'Lavender Essence',
      description: 'Floral raw material',
      category: 'ESSENCE',
      unitOfMeasure: 'MILLILITER',
      essenceReference: '',
      productionTypeCode: '',
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
