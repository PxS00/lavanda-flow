import { HttpErrorResponse } from '@angular/common/http';
import { Component, input, output } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { By } from '@angular/platform-browser';
import { Observable, Subject, of } from 'rxjs';

import { InventoryItemDto } from '../../../catalog/data-access/inventory-item.dto';
import { InventoryItemApiService } from '../../../catalog/data-access/inventory-item-api.service';
import { InventoryItemSelector } from '../../../catalog/ui/inventory-item-selector/inventory-item-selector';
import { SupplierDto } from '../../../suppliers/data-access/supplier.dto';
import { StockReceiptApiService } from '../../data-access/stock-receipt-api.service';
import { RegisterStockReceiptDto } from '../../data-access/stock-receipt.dto';
import { SupplierSelector } from '../../ui/supplier-selector/supplier-selector';
import { StockReceiptPage } from './stock-receipt-page';

@Component({ selector: 'app-inventory-item-selector', template: '' })
class InventoryItemSelectorStub {
  readonly selected = input<InventoryItemDto | null>(null);
  readonly disabled = input(false);
  readonly selectionChange = output<InventoryItemDto | null>();
}

@Component({ selector: 'app-supplier-selector', template: '' })
class SupplierSelectorStub {
  readonly selected = input<SupplierDto | null>(null);
  readonly disabled = input(false);
  readonly selectionChange = output<SupplierDto | null>();
}

describe('StockReceiptPage', () => {
  const item: InventoryItemDto = {
    id: 'bd194732-51cf-4f73-bc5d-3a9f9337adcc',
    name: 'Lavender Essence',
    description: null,
    category: 'ESSENCE',
    unitOfMeasure: 'MILLILITER',
    active: true,
    essenceReference: null,
    productionTypeCode: null,
  };
  const supplier: SupplierDto = {
    id: '9a38562f-e43c-4565-9f79-cd75bc08e39d',
    name: 'Lavender Supply',
    identifier: 'SUP-01',
    contact: null,
    notes: null,
    active: true,
  };
  const receipt: RegisterStockReceiptDto = {
    batchId: 'b78247ac-5e22-4097-a609-d396c81fab64',
    movementId: '2b459b94-25e0-4fbf-bb6e-8bd2d92446ff',
    inventoryItemId: item.id,
    supplierId: supplier.id,
    lotCode: 'LOT-96',
    quantity: 25.5,
    receivedAt: '2026-09-01',
    expiresAt: '2027-09-01',
    reason: 'Purchase receipt',
    occurredAt: '2026-09-01T20:30:00Z',
  };

  let fixture: ComponentFixture<StockReceiptPage>;
  let response: Subject<RegisterStockReceiptDto>;
  let register: ReturnType<
    typeof vi.fn<(request: unknown) => Observable<RegisterStockReceiptDto>>
  >;

  beforeEach(async () => {
    response = new Subject<RegisterStockReceiptDto>();
    register = vi.fn(() => response);

    TestBed.configureTestingModule({
      imports: [StockReceiptPage],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { queryParamMap: of(convertToParamMap({})) },
        },
        { provide: InventoryItemApiService, useValue: { getById: vi.fn(() => of(item)) } },
        { provide: StockReceiptApiService, useValue: { register } },
      ],
    });

    TestBed.overrideComponent(StockReceiptPage, {
      remove: { imports: [InventoryItemSelector, SupplierSelector] },
      add: { imports: [InventoryItemSelectorStub, SupplierSelectorStub] },
    });

    await TestBed.compileComponents();
    fixture = TestBed.createComponent(StockReceiptPage);
    fixture.detectChanges();
  });

  it('should submit one valid receipt and lock the completed transaction against repeat submission', () => {
    selectItem();
    selectSupplier();
    setValidForm();

    submit();

    expect(register).toHaveBeenCalledTimes(1);
    expect(register).toHaveBeenCalledWith({
      inventoryItemId: item.id,
      supplierId: supplier.id,
      lotCode: 'LOT-96',
      quantity: 25.5,
      receivedAt: '2026-09-01',
      expiresAt: '2027-09-01',
      reason: 'Purchase receipt',
    });

    response.next(receipt);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Entrada cadastrada');
    expect(fixture.nativeElement.textContent).toContain(receipt.batchId);
    expect(fixture.nativeElement.textContent).toContain(receipt.movementId);
    expect(findButton('Cadastrar entrada')).toBeUndefined();

    const operationsLink = Array.from(fixture.nativeElement.querySelectorAll('a')).find(
      (link) => (link as HTMLAnchorElement).textContent?.includes('Abrir operações do item'),
    ) as HTMLAnchorElement | undefined;
    expect(operationsLink?.getAttribute('href')).toBe(`/inventory/items/${item.id}`);
  });

  it('should submit without a supplier and normalize blank optional values to null', () => {
    selectItem();
    fixture.componentInstance.receiptModel.set({
      lotCode: '   ',
      quantity: '1.000001',
      receivedAt: '2026-09-01',
      expiresAt: '',
      reason: '   ',
    });
    fixture.detectChanges();

    submit();

    expect(register).toHaveBeenCalledWith({
      inventoryItemId: item.id,
      supplierId: null,
      lotCode: null,
      quantity: 1.000001,
      receivedAt: '2026-09-01',
      expiresAt: null,
      reason: null,
    });
  });

  it('should reject invalid quantity and missing received date before submission', () => {
    selectItem();
    fixture.componentInstance.receiptModel.set({
      lotCode: '',
      quantity: '0.000000',
      receivedAt: '',
      expiresAt: '2020-01-01',
      reason: '',
    });
    fixture.detectChanges();

    submit();
    fixture.detectChanges();

    expect(register).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('quantidade positiva');
    expect(fixture.nativeElement.textContent).toContain('Data de entrada é obrigatória.');
  });

  it('should not impose a client-side ordering rule on expiration and received dates', () => {
    selectItem();
    fixture.componentInstance.receiptModel.set({
      lotCode: '',
      quantity: '5',
      receivedAt: '2026-09-01',
      expiresAt: '2026-08-31',
      reason: '',
    });
    fixture.detectChanges();

    submit();

    expect(register).toHaveBeenCalledTimes(1);
    expect(register.mock.calls[0]?.[0]).toMatchObject({
      receivedAt: '2026-09-01',
      expiresAt: '2026-08-31',
    });
  });

  it('should prevent duplicate submissions while a receipt request is in flight', () => {
    selectItem();
    setValidForm();

    submit();
    submit();

    expect(register).toHaveBeenCalledTimes(1);
  });

  it.each([
    [400, 'VALIDATION_ERROR', 'Receipt data is invalid.'],
    [404, 'SUPPLIER_NOT_FOUND', 'Supplier not found.'],
    [422, 'INVENTORY_ITEM_INACTIVE', 'Inventory item must be active to receive stock.'],
  ])('should surface backend status %i as actionable feedback', (status, code, message) => {
    selectItem();
    setValidForm();
    submit();

    response.error(apiError(status, code, message));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(
      status === 400 ? 'Revise os dados informados.' : status === 404 ? 'Fornecedor não encontrado.' : 'Não foi possível concluir a operação no estado atual.',
    );
  });

  function selectItem(): void {
    const selector = fixture.debugElement.query(By.directive(InventoryItemSelectorStub))
      .componentInstance as InventoryItemSelectorStub;
    selector.selectionChange.emit(item);
    fixture.detectChanges();
  }

  function selectSupplier(): void {
    const selector = fixture.debugElement.query(By.directive(SupplierSelectorStub))
      .componentInstance as SupplierSelectorStub;
    selector.selectionChange.emit(supplier);
    fixture.detectChanges();
  }

  function setValidForm(): void {
    fixture.componentInstance.receiptModel.set({
      lotCode: 'LOT-96',
      quantity: '25.500000',
      receivedAt: '2026-09-01',
      expiresAt: '2027-09-01',
      reason: 'Purchase receipt',
    });
    fixture.detectChanges();
  }

  function submit(): void {
    findButton('Cadastrar entrada')?.click();
    fixture.detectChanges();
  }

  function findButton(text: string): HTMLButtonElement | undefined {
    return Array.from(fixture.nativeElement.querySelectorAll('button')).find(
      (candidate) => (candidate as HTMLButtonElement).textContent?.includes(text),
    ) as HTMLButtonElement | undefined;
  }

  function apiError(status: number, code: string, message: string): HttpErrorResponse {
    return new HttpErrorResponse({
      status,
      error: {
        timestamp: '2026-09-01T20:30:00Z',
        status,
        error:
          status === 400 ? 'Bad Request' : status === 404 ? 'Not Found' : 'Unprocessable Content',
        code,
        message,
        path: '/api/v1/inventory/receipts',
        details: {},
      },
    });
  }
});
