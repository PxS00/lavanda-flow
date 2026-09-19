import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import {
  ProductionExecutionDetailsDto,
  ProductionExecutionDto,
  ProductionExecutionHistoryPageDto,
  RegisterProductionRequest,
} from './production-execution.dto';
import { ProductionExecutionApiService } from './production-execution-api.service';

describe('ProductionExecutionApiService', () => {
  const executionsUrl = 'https://api.example.test/api/v1/production/executions';
  const requestBody: RegisterProductionRequest = {
    formulaId: 'formula-1',
    outputQuantity: '50.5',
    sourceAllocations: [
      { batchId: 'batch-a', quantity: '2.25' },
      { batchId: 'batch-b', quantity: '1.5' },
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
    outputQuantity: '50.5',
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
        quantity: '2.25',
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

  it('should request backend-paginated history with optional inclusive dates', () => {
    const page: ProductionExecutionHistoryPageDto = {
      content: [],
      page: 2,
      size: 50,
      totalElements: 0,
      totalPages: 0,
    };
    service.search({ from: '2026-09-01', to: '2026-09-30', page: 2, size: 50 }).subscribe();

    const request = httpTesting.expectOne(
      `${executionsUrl}?page=2&size=50&from=2026-09-01&to=2026-09-30`,
    );
    expect(request.request.method).toBe('GET');
    request.flush(page);

    service.search({ page: 0, size: 20 }).subscribe();
    const unfiltered = httpTesting.expectOne(`${executionsUrl}?page=0&size=20`);
    expect(unfiltered.request.method).toBe('GET');
    unfiltered.flush({ ...page, page: 0, size: 20 });
  });

  it('should fetch direct execution detail while preserving decimal strings', () => {
    const detail: ProductionExecutionDetailsDto = {
      executionId: 'execution/1',
      formulaId: 'formula-1',
      outputInventoryItemId: 'output-item',
      outputItemName: 'Sabonete',
      outputUnitOfMeasure: 'MILLILITER',
      outputBatchId: 'output-batch',
      outputQuantity: '9999999999999.123456',
      lotCode: 'LOT-1',
      lotCodeMode: 'MANUAL',
      productionDate: '2026-09-04',
      outputReceivedAt: '2026-09-04',
      outputExpiresAt: null,
      completedAt: '2026-09-04T18:00:00Z',
      consumptions: [],
    };
    let received: ProductionExecutionDetailsDto | undefined;
    service.getById('execution/1').subscribe((response) => (received = response));

    const request = httpTesting.expectOne(`${executionsUrl}/execution%2F1`);
    expect(request.request.method).toBe('GET');
    request.flush(detail);

    expect(received?.outputQuantity).toBe('9999999999999.123456');
  });
});
