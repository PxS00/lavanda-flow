import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import { RegisterFefoWithdrawalDto, RegisterFefoWithdrawalRequest } from './fefo-withdrawal.dto';
import { FefoWithdrawalApiService } from './fefo-withdrawal-api.service';

describe('FefoWithdrawalApiService', () => {
  const inventoryItemId = 'bd194732-51cf-4f73-bc5d-3a9f9337adcc';
  const withdrawalsUrl = `https://api.example.test/api/v1/inventory/items/${inventoryItemId}/withdrawals`;
  const requestBody: RegisterFefoWithdrawalRequest = {
    quantity: 80,
    reason: 'Production',
  };
  const singleAllocation: RegisterFefoWithdrawalDto = {
    inventoryItemId,
    requestedQuantity: 80,
    allocatedQuantity: 80,
    allocations: [
      {
        batchId: 'b78247ac-5e22-4097-a609-d396c81fab64',
        movementId: '2b459b94-25e0-4fbf-bb6e-8bd2d92446ff',
        quantity: 80,
      },
    ],
  };

  let service: FefoWithdrawalApiService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'https://api.example.test/' },
      ],
    });

    service = TestBed.inject(FefoWithdrawalApiService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('should post the exact request to the inventory item withdrawal URL', () => {
    let result: RegisterFefoWithdrawalDto | undefined;
    service.register(inventoryItemId, requestBody).subscribe((response) => (result = response));

    const request = httpTesting.expectOne(withdrawalsUrl);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(requestBody);
    request.flush(singleAllocation);

    expect(result).toEqual(singleAllocation);
  });

  it('should propagate every allocation in a multi-batch withdrawal response', () => {
    const response: RegisterFefoWithdrawalDto = {
      ...singleAllocation,
      allocations: [
        { ...singleAllocation.allocations[0], quantity: 15 },
        {
          batchId: '5184d508-35eb-42de-a2a0-44c4f6c9b9ae',
          movementId: '12bf0c0d-93db-4a78-bb05-b9e5d0a1e15d',
          quantity: 65,
        },
      ],
    };
    let result: RegisterFefoWithdrawalDto | undefined;

    service.register(inventoryItemId, requestBody).subscribe((value) => (result = value));

    const request = httpTesting.expectOne(withdrawalsUrl);
    expect(request.request.body).toEqual(requestBody);
    request.flush(response);

    expect(result).toEqual(response);
  });
});
