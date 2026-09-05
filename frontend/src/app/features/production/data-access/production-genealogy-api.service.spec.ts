import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import { BatchGenealogyDto, GenealogyDirection } from './production-genealogy.dto';
import { ProductionGenealogyApiService } from './production-genealogy-api.service';

describe('ProductionGenealogyApiService', () => {
  const batchId = '70a3d964-54d0-42a2-8eb1-946ea88e6e0a';
  const genealogyUrl = `https://api.example.test/api/v1/production/genealogy/batches/${batchId}`;
  const response: BatchGenealogyDto = {
    direction: 'BOTH',
    rootBatch: batch('root', 'INTERNALLY_PRODUCED'),
    upstream: [
      {
        executionId: 'execution-1',
        formulaId: 'formula-1',
        productionDate: '2026-09-01',
        completedAt: '2026-09-01T12:00:00Z',
        consumedQuantity: 1.234567,
        sourceBatch: batch('source', 'EXTERNAL_OR_NON_PRODUCED'),
        outputBatch: batch('root', 'INTERNALLY_PRODUCED'),
        next: [],
      },
    ],
    downstream: [],
  };

  let service: ProductionGenealogyApiService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'https://api.example.test/' },
      ],
    });

    service = TestBed.inject(ProductionGenealogyApiService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it.each<GenealogyDirection>(['UPSTREAM', 'DOWNSTREAM', 'BOTH'])(
    'should request stable batch identity with the %s wire direction',
    (direction) => {
      let received: BatchGenealogyDto | undefined;
      service.getBatchGenealogy(batchId, direction).subscribe((result) => (received = result));

      const request = httpTesting.expectOne(
        (candidate) =>
          candidate.url === genealogyUrl && candidate.params.get('direction') === direction,
      );
      expect(request.request.method).toBe('GET');
      request.flush({ ...response, direction });

      expect(received?.upstream[0].next).toEqual([]);
      expect(received?.upstream[0].consumedQuantity).toBe(1.234567);
    },
  );
});

function batch(batchId: string, origin: 'INTERNALLY_PRODUCED' | 'EXTERNAL_OR_NON_PRODUCED') {
  return {
    batchId,
    origin,
    inventoryItemId: `item-${batchId}`,
    itemName: `Item ${batchId}`,
    itemCategory: 'OTHER',
    unitOfMeasure: 'KILOGRAM',
    supplierId: origin === 'EXTERNAL_OR_NON_PRODUCED' ? 'supplier-1' : null,
    lotCode: `LOT-${batchId}`,
    receivedAt: '2026-09-01',
    expiresAt: null,
  } as const;
}
