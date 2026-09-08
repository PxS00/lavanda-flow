import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import { StockMaintenanceMovementDto, StockMaintenanceRequest } from './stock-maintenance.dto';
import { StockMaintenanceApiService } from './stock-maintenance-api.service';

describe('StockMaintenanceApiService', () => {
  const batchId = 'b78247ac-5e22-4097-a609-d396c81fab64';
  const batchesUrl = `https://api.example.test/api/v1/inventory/batches/${batchId}`;
  const request: StockMaintenanceRequest = {
    quantity: '9999999999999.123456',
    reason: 'Contagem física',
  };
  const response: StockMaintenanceMovementDto = {
    movementId: '2b459b94-25e0-4fbf-bb6e-8bd2d92446ff',
    batchId,
    type: 'ADJUSTMENT_IN',
    quantity: request.quantity,
    resultingBalance: '9999999999999.123457',
    reason: request.reason,
    occurredAt: '2026-09-08T15:00:00Z',
  };

  let service: StockMaintenanceApiService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'https://api.example.test/' },
      ],
    });

    service = TestBed.inject(StockMaintenanceApiService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it.each([
    ['adjust', 'adjustments', 'ADJUSTMENT_IN'],
    ['registerLoss', 'losses', 'LOSS'],
    ['registerExpiredDisposal', 'expired-disposals', 'EXPIRED_DISPOSAL'],
  ] as const)('should post an exact decimal %s request to %s', (method, path, type) => {
    let result: StockMaintenanceMovementDto | undefined;
    service[method](batchId, request).subscribe((value) => (result = value));

    const httpRequest = httpTesting.expectOne(`${batchesUrl}/${path}`);
    expect(httpRequest.request.method).toBe('POST');
    expect(httpRequest.request.body).toEqual(request);
    httpRequest.flush({ ...response, type });

    expect(result).toEqual({ ...response, type });
  });
});
