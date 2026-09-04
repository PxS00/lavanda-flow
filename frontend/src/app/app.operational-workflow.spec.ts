import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { routes } from './app.routes';
import { API_BASE_URL } from './core/config/api-base-url.token';

describe('operational UI workflow', () => {
  const apiUrl = 'https://api.example.test/api/v1';
  const itemId = 'item-1';
  const item = {
    id: itemId, name: 'Essência de lavanda', description: null, category: 'ESSENCE',
    unitOfMeasure: 'MILLILITER', active: true, essenceReference: '027', productionTypeCode: 'BDS',
  };
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes), provideHttpClient(), provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: apiUrl },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('composes shell, catalog, receipt, workspace refresh, FEFO, alerts, and stable item navigation', async () => {
    const harness = await RouterTestingHarness.create('/dashboard');
    expect(harness.routeNativeElement?.textContent).toContain('Painel');
    findLink(harness, 'Estoque').click();
    await harness.fixture.whenStable();
    http.expectOne(`${apiUrl}/inventory-items?page=0&size=20`).flush(page([item]));
    harness.fixture.detectChanges();
    expect(harness.routeNativeElement?.textContent).toContain('Catálogo de estoque');

    const catalogLink = findLink(harness, 'Essência de lavanda');
    catalogLink.click();
    await harness.fixture.whenStable();
    http.expectOne(`${apiUrl}/inventory-items/${itemId}`).flush(item);
    harness.fixture.detectChanges();
    findLink(harness, 'Abrir operações de estoque').click();
    await harness.fixture.whenStable();
    flushWorkspace(http, 40);
    harness.fixture.detectChanges();
    expect(harness.routeNativeElement?.textContent).toContain('Operações de estoque');
    expect(harness.routeNativeElement?.textContent).toContain('Quantidade física atual');
    expect(harness.routeNativeElement?.textContent).toContain('LOTE-INICIAL');
    expect(harness.routeNativeElement?.textContent).toContain('Mínimo atual: 50');
    expect(harness.routeNativeElement?.textContent).toContain('Histórico de movimentações');
    expect(harness.routeNativeElement?.textContent).toContain('Inventário inicial');

    await TestBed.inject(Router).navigateByUrl(`/receipts?inventoryItemId=${itemId}`);
    await harness.fixture.whenStable();
    http.expectOne(`${apiUrl}/inventory-items/${itemId}`).flush(item);
    const receiptInputs = Array.from(harness.routeNativeElement!.querySelectorAll('input')) as HTMLInputElement[];
    setInput(receiptInputs.find((input) => input.type === 'text')!, '25');
    setInput(receiptInputs.find((input) => input.type === 'date')!, '2026-09-01');
    findButton(harness, 'Cadastrar entrada').click();
    http.expectOne(`${apiUrl}/inventory/receipts`).flush(receiptSuccess());
    harness.fixture.detectChanges();
    expect(harness.routeNativeElement?.textContent).toContain('Entrada cadastrada');
    findLink(harness, 'Abrir operações do item').click();
    await harness.fixture.whenStable();
    flushWorkspace(http, 65);
    harness.fixture.detectChanges();
    expect(harness.routeNativeElement?.textContent).toContain('65');

    const quantity = Array.from(harness.routeNativeElement!.querySelectorAll('input'))[0] as HTMLInputElement;
    quantity.value = '30';
    quantity.dispatchEvent(new Event('input'));
    harness.fixture.detectChanges();
    findButton(harness, 'Revisar saída').click();
    harness.fixture.detectChanges();
    findButton(harness, 'Confirmar saída').click();

    const withdrawal = http.expectOne(`${apiUrl}/inventory/items/${itemId}/withdrawals`);
    expect(withdrawal.request.method).toBe('POST');
    expect(withdrawal.request.body).toEqual({ quantity: 30, reason: null });
    withdrawal.flush({
      inventoryItemId: itemId, requestedQuantity: 30, allocatedQuantity: 30,
      allocations: [
        { batchId: 'batch-a', movementId: 'movement-a', quantity: 10 },
        { batchId: 'batch-b', movementId: 'movement-b', quantity: 20 },
      ],
    });
    flushWorkspace(http, 10, false);
    harness.fixture.detectChanges();
    expect(harness.routeNativeElement?.textContent).toContain('Alocações registradas');
    expect(harness.routeNativeElement?.textContent).toContain('batch-a');
    expect(harness.routeNativeElement?.textContent).toContain('batch-b');

    await TestBed.inject(Router).navigateByUrl('/inventory/alerts');
    await harness.fixture.whenStable();
    http.expectOne(`${apiUrl}/inventory/alerts/low-stock`).flush({
      asOfDate: '2026-09-01', alerts: [{ inventoryItemId: itemId, name: item.name,
        unitOfMeasure: 'MILLILITER', availableQuantity: 10, minimumQuantity: 50, deficitQuantity: 40 }],
    });
    http.expectOne(`${apiUrl}/inventory/alerts/expiration`).flush({
      asOfDate: '2026-09-01', windowDays: 30, alerts: [],
    });
    harness.fixture.detectChanges();
    findLink(harness, 'Abrir item').click();
    await harness.fixture.whenStable();
    flushWorkspace(http, 10);
    expect(TestBed.inject(Router).url).toBe(`/inventory/items/${itemId}`);
  });

  it('exposes a failed receipt write through the shared error UI without stale success', async () => {
    const harness = await RouterTestingHarness.create(`/receipts?inventoryItemId=${itemId}`);
    http.expectOne(`${apiUrl}/inventory-items/${itemId}`).flush(item);

    const inputs = Array.from(harness.routeNativeElement!.querySelectorAll('input')) as HTMLInputElement[];
    setInput(inputs.find((input) => input.type === 'text')!, '25');
    setInput(inputs.find((input) => input.type === 'date')!, '2026-09-01');
    findButton(harness, 'Cadastrar entrada').click();
    const failedReceipt = http.expectOne(`${apiUrl}/inventory/receipts`);
    failedReceipt.flush(apiError('INVALID_BATCH_DATA'), { status: 400, statusText: 'Bad Request' });
    harness.fixture.detectChanges();
    expect(harness.routeNativeElement?.textContent).toContain('Os dados do lote são inválidos.');
    expect(harness.routeNativeElement?.textContent).not.toContain('Entrada cadastrada');
  });
});

function page(content: readonly unknown[]) {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: content.length === 0 ? 0 : 1 };
}

function flushWorkspace(
  http: HttpTestingController,
  availableQuantity: number,
  includeMinimum = true,
): void {
  const overview = http.expectOne('https://api.example.test/api/v1/inventory/items/item-1/overview');
  overview.flush({ inventoryItemId: 'item-1', name: 'Essência de lavanda', category: 'ESSENCE',
    unitOfMeasure: 'MILLILITER', active: true, asOfDate: '2026-09-01', expirationWindowDays: 30,
    totalCurrentQuantity: availableQuantity, availableQuantity, minimumQuantity: 50, lowStock: true,
    outOfStock: false, nonZeroBatchCount: 2, nearestExpiration: '2026-09-15', expiredBatchCount: 0,
    expiringSoonBatchCount: 1 });
  http.expectOne('https://api.example.test/api/v1/inventory/items/item-1/batches').flush({ inventoryItemId: 'item-1',
    asOfDate: '2026-09-01', batches: [{ batchId: 'batch-initial', inventoryItemId: 'item-1', supplierId: null,
      lotCode: 'LOTE-INICIAL', initialQuantity: 80, currentQuantity: availableQuantity, receivedAt: '2026-08-01',
      expiresAt: '2026-09-15', status: 'AVAILABLE' }] });
  if (includeMinimum) {
    http.expectOne('https://api.example.test/api/v1/inventory/items/item-1/minimum-stock-level').flush({ inventoryItemId: 'item-1', minimumQuantity: 50 });
  }
  http.expectOne('https://api.example.test/api/v1/inventory/movements?inventoryItemId=item-1&page=0&size=20').flush(page([{ movementId: 'movement-initial', inventoryItemId: 'item-1', inventoryItemName: 'Essência de lavanda',
    unitOfMeasure: 'MILLILITER', inventoryItemActive: true, batchId: 'batch-initial', lotCode: 'LOTE-INICIAL',
    type: 'ENTRY', quantity: 80, reason: 'Inventário inicial', occurredAt: '2026-08-01T10:00:00Z' }]));
}

function findButton(harness: RouterTestingHarness, text: string): HTMLButtonElement {
  return Array.from(harness.routeNativeElement!.querySelectorAll('button')).find((button) => button.textContent?.includes(text)) as HTMLButtonElement;
}

function findLink(harness: RouterTestingHarness, text: string): HTMLAnchorElement {
  return Array.from(harness.routeNativeElement!.querySelectorAll('a')).find((link) => link.textContent?.includes(text)) as HTMLAnchorElement;
}

function setInput(input: HTMLInputElement, value: string): void {
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

function apiError(code: string) {
  return { timestamp: '2026-09-01T12:00:00Z', status: 400, error: 'Bad Request', code,
    message: 'invalid', path: '/api/v1/inventory/receipts', details: null };
}

function receiptSuccess() {
  return { batchId: 'batch-new', movementId: 'movement-new', inventoryItemId: 'item-1', supplierId: null,
    lotCode: null, quantity: 25, receivedAt: '2026-09-01', expiresAt: null, reason: null,
    occurredAt: '2026-09-01T12:00:00Z' };
}
