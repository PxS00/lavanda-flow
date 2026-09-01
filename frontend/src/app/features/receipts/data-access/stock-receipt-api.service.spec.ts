import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import { RegisterStockReceiptDto, RegisterStockReceiptRequest } from './stock-receipt.dto';
import { StockReceiptApiService } from './stock-receipt-api.service';

describe('StockReceiptApiService', () => {
  const receiptsUrl = 'https://api.example.test/api/v1/inventory/receipts';
  const requestBody: RegisterStockReceiptRequest = {
    inventoryItemId: 'bd194732-51cf-4f73-bc5d-3a9f9337adcc',
    supplierId: '9a38562f-e43c-4565-9f79-cd75bc08e39d',
    lotCode: 'LOT-96',
    quantity: 25.5,
    receivedAt: '2026-09-01',
    expiresAt: '2027-09-01',
    reason: 'Purchase receipt',
  };
  const response: RegisterStockReceiptDto = {
    batchId: 'b78247ac-5e22-4097-a609-d396c81fab64',
    movementId: '2b459b94-25e0-4fbf-bb6e-8bd2d92446ff',
    ...requestBody,
    occurredAt: '2026-09-01T20:30:00Z',
  };

  let service: StockReceiptApiService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'https://api.example.test/' },
      ],
    });

    service = TestBed.inject(StockReceiptApiService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('should register one stock receipt with the exact request body', () => {
    let result: RegisterStockReceiptDto | undefined;

    service.register(requestBody).subscribe((value) => {
      result = value;
    });

    const request = httpTesting.expectOne(receiptsUrl);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(requestBody);
    request.flush(response);

    expect(result).toEqual(response);
  });

  it('should preserve a null optional supplier and optional receipt fields', () => {
    const body: RegisterStockReceiptRequest = {
      inventoryItemId: requestBody.inventoryItemId,
      supplierId: null,
      lotCode: null,
      quantity: 1,
      receivedAt: '2026-09-01',
      expiresAt: null,
      reason: null,
    };

    service.register(body).subscribe();

    const request = httpTesting.expectOne(receiptsUrl);
    expect(request.request.body).toEqual(body);
    request.flush({ ...response, ...body });
  });
});
