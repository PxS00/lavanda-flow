import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, ParamMap, convertToParamMap, provideRouter, Router } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';

import { SupplierDto, UpdateSupplierRequest } from '../../data-access/supplier.dto';
import { SupplierApiService } from '../../data-access/supplier-api.service';
import { SupplierEditPage } from './supplier-edit-page';

describe('SupplierEditPage', () => {
  const supplierId = '53b1fdb4-72ab-41bb-b9e7-381d922d69a8';
  const supplier: SupplierDto = {
    id: supplierId,
    name: 'Lavanda Supplies',
    identifier: '12.345.678/0001-90',
    contact: 'suppliers@example.test',
    notes: 'Preferred supplier',
    active: true,
  };

  let fixture: ComponentFixture<SupplierEditPage>;
  let response: Subject<SupplierDto>;
  let updateResponse: Subject<SupplierDto>;
  let routeParams: Subject<ParamMap>;
  let getById: ReturnType<typeof vi.fn<(id: string) => Observable<SupplierDto>>>;
  let update: ReturnType<
    typeof vi.fn<(id: string, request: UpdateSupplierRequest) => Observable<SupplierDto>>
  >;
  let router: Router;

  beforeEach(async () => {
    response = new Subject<SupplierDto>();
    updateResponse = new Subject<SupplierDto>();
    routeParams = new Subject<ParamMap>();
    getById = vi.fn(() => response);
    update = vi.fn(() => updateResponse);

    await TestBed.configureTestingModule({
      imports: [SupplierEditPage],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap: routeParams } },
        { provide: SupplierApiService, useValue: { getById, update } },
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    fixture = TestBed.createComponent(SupplierEditPage);
    fixture.detectChanges();
    routeParams.next(convertToParamMap({ supplierId }));
    fixture.detectChanges();
  });

  it('should load and prefill the supported supplier fields', () => {
    response.next(supplier);
    fixture.detectChanges();

    expect(getById).toHaveBeenCalledWith(supplierId);
    expect(fixture.componentInstance.editForm.getRawValue()).toEqual({
      name: supplier.name,
      identifier: supplier.identifier,
      contact: supplier.contact,
      notes: supplier.notes,
      active: true,
    });
    expect(fixture.nativeElement.textContent).toContain('Fornecedor ativo');
  });

  it('should submit supported fields and navigate only after backend success', () => {
    response.next(supplier);
    fixture.detectChanges();
    fixture.componentInstance.editForm.setValue({
      name: 'Lavanda Atualizada',
      identifier: 'ID-223',
      contact: 'updated@example.test',
      notes: 'Updated notes',
      active: false,
    });
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    submitForm();

    expect(update).toHaveBeenCalledWith(supplierId, {
      name: 'Lavanda Atualizada',
      identifier: 'ID-223',
      contact: 'updated@example.test',
      notes: 'Updated notes',
      active: false,
    });
    expect(navigate).not.toHaveBeenCalled();

    updateResponse.next({ ...supplier, name: 'Lavanda Atualizada', active: false });

    expect(navigate).toHaveBeenCalledWith(['/suppliers', supplierId]);
  });

  it('should normalize blank optional fields to null', () => {
    response.next(supplier);
    fixture.detectChanges();
    fixture.componentInstance.editForm.patchValue({
      identifier: '  ',
      contact: '',
      notes: ' ',
    });

    submitForm();

    expect(update).toHaveBeenCalledWith(supplierId, expect.objectContaining({
      identifier: null,
      contact: null,
      notes: null,
    }));
  });

  it('should allow reactivation', () => {
    response.next({ ...supplier, active: false });
    fixture.detectChanges();
    fixture.componentInstance.editForm.controls.active.setValue(true);

    submitForm();

    expect(update).toHaveBeenCalledWith(supplierId, expect.objectContaining({ active: true }));
  });

  it('should prevent duplicate submissions while a save is pending', () => {
    response.next(supplier);
    fixture.detectChanges();

    submitForm();
    submitForm();

    expect(update).toHaveBeenCalledTimes(1);
    expect((fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement).disabled)
      .toBe(true);
  });

  it('should prevent blank names before submitting', () => {
    response.next(supplier);
    fixture.detectChanges();
    fixture.componentInstance.editForm.controls.name.setValue('  ');

    submitForm();

    expect(update).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Nome é obrigatório');
  });

  it('should render field validation errors and preserve entered form values', () => {
    response.next(supplier);
    fixture.detectChanges();
    fixture.componentInstance.editForm.controls.name.setValue('Dados preservados');
    update.mockReturnValueOnce(
      throwError(() => apiError(400, 'VALIDATION_ERROR', { name: 'must not be blank' })),
    );

    submitForm();

    expect(fixture.nativeElement.textContent).toContain('Verifique o valor informado.');
    expect(fixture.componentInstance.editForm.controls.name.value).toBe('Dados preservados');
  });

  it('should preserve the form after an infrastructure save failure', () => {
    response.next(supplier);
    fixture.detectChanges();
    fixture.componentInstance.editForm.controls.name.setValue('Dados preservados');
    update.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));

    submitForm();

    expect(fixture.nativeElement.textContent).toContain('Ocorreu um erro no servidor. Tente novamente.');
    expect(fixture.componentInstance.editForm.controls.name.value).toBe('Dados preservados');
    expect((fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement).disabled)
      .toBe(false);
  });

  it('should render a not-found load error and retry', () => {
    response.error(apiError(404, 'SUPPLIER_NOT_FOUND'));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Fornecedor não encontrado.');

    getById.mockReturnValueOnce(of(supplier));
    clickButton('Tentar novamente');

    expect(getById).toHaveBeenCalledTimes(2);
    expect(fixture.componentInstance.editForm.controls.name.value).toBe(supplier.name);
  });

  function submitForm(): void {
    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new SubmitEvent('submit', { bubbles: true, cancelable: true }));
    fixture.detectChanges();
  }

  function clickButton(label: string): void {
    const button = Array.from(fixture.nativeElement.querySelectorAll('button')).find(
      (candidate) => (candidate as HTMLButtonElement).textContent?.trim() === label,
    ) as HTMLButtonElement | undefined;
    button?.click();
    fixture.detectChanges();
  }

  function apiError(
    status: number,
    code: string,
    details?: Record<string, string>,
  ): HttpErrorResponse {
    return new HttpErrorResponse({
      status,
      error: {
        timestamp: '2026-09-01T12:00:00Z',
        status,
        error: status === 404 ? 'Not Found' : 'Bad Request',
        code,
        message: 'Supplier request failed',
        path: `/api/v1/suppliers/${supplierId}`,
        ...(details === undefined ? {} : { details }),
      },
    });
  }
});
