import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import { ProductionExecutionDto, RegisterProductionRequest } from './production-execution.dto';
import { ProductionExecutionApiService } from './production-execution-api.service';

describe('ProductionExecutionApiService', () => {
  const executionsUrl = 'https://api.example.test/api/v1/production/executions';
  const requestBody: RegisterProductionRequest = {
    formulaId: 'formula-1',
    outputQuantity: 50.5,
    sourceAllocations: [
      { batchId: 'batch-a', quantity: 2.25 },
      { batchId: 'batch-b', quantity: 1.5 },
    ],
    productionDate: '2026-09-04',
    outputReceivedAt: '2026-09-04',
    outputExpiresAt: '2027-09-04',
    lotCodeMode: 'GENERATED',
    manualLotCode: null,
  };
  const responseBody: ProductionExecutionDto = {
    executionId: 'execution-1',
    formulaId: 'formula-1',
    outputInventoryItemId: 'output-item',
    outputBatchId: 'output-batch',
    outputQuantity: 50.5,
    lotCode: 'BDS-014-003-09-2026',
    lotCodeMode: 'GENERATED',
    productionDate: '2026-09-04',
    outputReceivedAt: '2026-09-04',
    outputExpiresAt: '2027-09-04',
    completedAt: '2026-09-04T18:00:00Z',
    consumptions: [
      {
        sourceBatchId: 'batch-a',
        sourceInventoryItemId: 'ingredient-a',
        movementId: 'movement-1',
        quantity: 2.25,
      },
    ],
  };

  let service: ProductionExecutionApiService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'https://api.example.test/' },
      ],
    });

    service = TestBed.inject(ProductionExecutionApiService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('should register production with the exact backend request body', () => {
    let received: ProductionExecutionDto | undefined;
    service.register(requestBody).subscribe((response) => (received = response));

    const request = httpTesting.expectOne(executionsUrl);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(requestBody);
    request.flush(responseBody);

    expect(received).toEqual(responseBody);
  });
});
