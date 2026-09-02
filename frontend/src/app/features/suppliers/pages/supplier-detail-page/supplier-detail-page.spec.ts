import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, ParamMap, convertToParamMap, provideRouter } from '@angular/router';
import { Observable, Subject } from 'rxjs';

import { SupplierDto } from '../../data-access/supplier.dto';
import { SupplierApiService } from '../../data-access/supplier-api.service';
import { SupplierDetailPage } from './supplier-detail-page';

describe('SupplierDetailPage', () => {
  const supplierId = '53b1fdb4-72ab-41bb-b9e7-381d922d69a8';
  const supplier: SupplierDto = {
    id: supplierId,
    name: 'Lavanda Supplies',
    identifier: '12.345.678/0001-90',
    contact: 'suppliers@example.test',
    notes: 'Preferred supplier',
    active: true,
  };

  let fixture: ComponentFixture<SupplierDetailPage>;
  let response: Subject<SupplierDto>;
  let routeParams: Subject<ParamMap>;
  let getById: ReturnType<typeof vi.fn<(id: string) => Observable<SupplierDto>>>;

  beforeEach(async () => {
    response = new Subject<SupplierDto>();
    routeParams = new Subject<ParamMap>();
    getById = vi.fn(() => response);

    await TestBed.configureTestingModule({
      imports: [SupplierDetailPage],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap: routeParams } },
        { provide: SupplierApiService, useValue: { getById } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SupplierDetailPage);
    fixture.detectChanges();
    routeParams.next(convertToParamMap({ supplierId }));
    fixture.detectChanges();
  });

  it('should use the direct route ID and show loading feedback', () => {
    expect(getById).toHaveBeenCalledWith(supplierId);
    expect(fixture.nativeElement.textContent).toContain('Carregando fornecedor...');
  });

  it('should render the returned supplier', () => {
    response.next(supplier);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Lavanda Supplies');
    expect(fixture.nativeElement.textContent).toContain('12.345.678/0001-90');
    expect(fixture.nativeElement.textContent).toContain('Preferred supplier');
  });

  it('should render the mapped not-found state', () => {
    response.error(apiError(404, 'SUPPLIER_NOT_FOUND', 'Supplier not found.'));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Fornecedor não encontrado.');
  });

  it('should render a generic server error and retry', () => {
    response.error(apiError(500, 'INTERNAL_ERROR', 'Internal details'));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(
      'Ocorreu um erro no servidor. Tente novamente.',
    );

    response = new Subject<SupplierDto>();
    getById.mockReturnValue(response);
    const retryButton = Array.from(fixture.nativeElement.querySelectorAll('button')).find(
      (button) => (button as HTMLButtonElement).textContent?.trim() === 'Tentar novamente',
    ) as HTMLButtonElement | undefined;

    retryButton?.click();
    fixture.detectChanges();

    expect(getById).toHaveBeenCalledTimes(2);
    expect(getById).toHaveBeenLastCalledWith(supplierId);
  });

  it('should keep the newest supplier when route parameters change', () => {
    const firstResponse = response;
    const secondResponse = new Subject<SupplierDto>();
    const secondSupplier: SupplierDto = {
      ...supplier,
      id: 'second-supplier',
      name: 'Second supplier',
    };
    getById.mockReturnValueOnce(secondResponse);

    routeParams.next(convertToParamMap({ supplierId: secondSupplier.id }));
    fixture.detectChanges();
    firstResponse.next(supplier);
    fixture.detectChanges();

    expect(getById).toHaveBeenLastCalledWith(secondSupplier.id);
    expect(fixture.nativeElement.textContent).toContain('Carregando fornecedor...');

    secondResponse.next(secondSupplier);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Second supplier');
    expect(fixture.nativeElement.textContent).not.toContain('Lavanda Supplies');
  });

  function apiError(status: number, code: string, message: string): HttpErrorResponse {
    return new HttpErrorResponse({
      status,
      error: {
        timestamp: '2026-09-01T12:00:00Z',
        status,
        error: status === 404 ? 'Not Found' : 'Internal Server Error',
        code,
        message,
        path: `/api/v1/suppliers/${supplierId}`,
        details: {},
      },
    });
  }
});
