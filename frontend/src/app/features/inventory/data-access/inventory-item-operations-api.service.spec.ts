import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import {
  BatchInventoryDto,
  ConfigureMinimumStockLevelRequest,
  InventoryItemOverviewDto,
  MinimumStockLevelDto,
} from './inventory-operations.dto';
import { InventoryItemOperationsApiService } from './inventory-item-operations-api.service';

describe('InventoryItemOperationsApiService', () => {
  const inventoryItemId = 'bd194732-51cf-4f73-bc5d-3a9f9337adcc';
  const inventoryItemsUrl = 'https://api.example.test/api/v1/inventory/items';
  const overview: InventoryItemOverviewDto = {
    inventoryItemId,
    name: 'Lavender Essence',
    category: 'ESSENCE',
    unitOfMeasure: 'MILLILITER',
    active: true,
    asOfDate: '2026-09-01',
    expirationWindowDays: 30,
    totalCurrentQuantity: 120,
    availableQuantity: 100,
    minimumQuantity: 25,
    lowStock: false,
    outOfStock: false,
    nonZeroBatchCount: 2,
    nearestExpiration: '2026-09-20',
    expiredBatchCount: 1,
    expiringSoonBatchCount: 1,
  };
  const batches: BatchInventoryDto = {
    inventoryItemId,
    asOfDate: '2026-09-01',
    batches: [],
  };
  const minimum: MinimumStockLevelDto = { inventoryItemId, minimumQuantity: 25 };

  let service: InventoryItemOperationsApiService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'https://api.example.test/' },
      ],
    });

    service = TestBed.inject(InventoryItemOperationsApiService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('should retrieve the item overview', () => {
    let result: InventoryItemOverviewDto | undefined;
    service.getOverview(inventoryItemId).subscribe((response) => (result = response));

    const request = httpTesting.expectOne(`${inventoryItemsUrl}/${inventoryItemId}/overview`);
    expect(request.request.method).toBe('GET');
    request.flush(overview);

    expect(result).toEqual(overview);
  });

  it('should retrieve batches without changing backend ordering', () => {
    let result: BatchInventoryDto | undefined;
    service.getBatches(inventoryItemId).subscribe((response) => (result = response));

    const request = httpTesting.expectOne(`${inventoryItemsUrl}/${inventoryItemId}/batches`);
    expect(request.request.method).toBe('GET');
    request.flush(batches);

    expect(result).toEqual(batches);
  });

  it('should retrieve the configured minimum stock level', () => {
    service.getMinimumStockLevel(inventoryItemId).subscribe();

    const request = httpTesting.expectOne(
      `${inventoryItemsUrl}/${inventoryItemId}/minimum-stock-level`,
    );
    expect(request.request.method).toBe('GET');
    request.flush(minimum);
  });

  it('should create or update the minimum stock level with the exact body', () => {
    const body: ConfigureMinimumStockLevelRequest = { minimumQuantity: 25.125 };
    service.configureMinimumStockLevel(inventoryItemId, body).subscribe();

    const request = httpTesting.expectOne(
      `${inventoryItemsUrl}/${inventoryItemId}/minimum-stock-level`,
    );
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(body);
    request.flush({ ...minimum, minimumQuantity: body.minimumQuantity });
  });

  it('should delete the configured minimum stock level', () => {
    service.deleteMinimumStockLevel(inventoryItemId).subscribe();

    const request = httpTesting.expectOne(
      `${inventoryItemsUrl}/${inventoryItemId}/minimum-stock-level`,
    );
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
  });
});
