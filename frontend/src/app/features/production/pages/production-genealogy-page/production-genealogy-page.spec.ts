import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { Observable, Subject } from 'rxjs';

import { ProductionGenealogyApiService } from '../../data-access/production-genealogy-api.service';
import {
  BatchGenealogyDto,
  GenealogyBatchDto,
  GenealogyDirection,
  GenealogyEdgeDto,
} from '../../data-access/production-genealogy.dto';
import { ProductionGenealogyPage } from './production-genealogy-page';

describe('ProductionGenealogyPage', () => {
  const batchId = 'root-batch';

  let fixture: ComponentFixture<ProductionGenealogyPage>;
  let responses: Subject<BatchGenealogyDto>[];
  let getBatchGenealogy: ReturnType<
    typeof vi.fn<
      (id: string, direction: GenealogyDirection) => Observable<BatchGenealogyDto>
    >
  >;

  beforeEach(async () => {
    responses = [new Subject<BatchGenealogyDto>()];
    getBatchGenealogy = vi.fn(() => responses.at(-1)!);

    await TestBed.configureTestingModule({
      imports: [ProductionGenealogyPage],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap: new Subject() } },
        { provide: ProductionGenealogyApiService, useValue: { getBatchGenealogy } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProductionGenealogyPage);
    fixture.detectChanges();
    const route = TestBed.inject(ActivatedRoute);
    (route.paramMap as Subject<ReturnType<typeof convertToParamMap>>).next(
      convertToParamMap({ batchId }),
    );
    fixture.detectChanges();
  });

  it('should render root context, an upstream chain, multiple sources, and exact quantities', () => {
    const externalSource = batch('external', 'Óleo essencial', 'EXTERNAL_OR_NON_PRODUCED');
    const secondSource = batch('external-2', 'Álcool', 'EXTERNAL_OR_NON_PRODUCED');
    const intermediate = batch('intermediate', 'Base de lavanda', 'INTERNALLY_PRODUCED');
    const root = batch(batchId, 'Perfume de lavanda', 'INTERNALLY_PRODUCED');
    const upstream = [
      edge('execution-root-a', intermediate, root, 2.125, [
        edge('execution-middle', externalSource, intermediate, 1.234567, [
          edge('execution-depth-3', secondSource, externalSource, 0.25),
        ]),
      ]),
      edge('execution-root-b', secondSource, root, 3.5),
    ];

    responses[0].next(genealogy('BOTH', root, upstream, []));
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(getBatchGenealogy).toHaveBeenCalledWith(batchId, 'BOTH');
    expect(text).toContain('Perfume de lavanda');
    expect(text).toContain('LOT-root-batch');
    expect(text).toContain('Produção interna');
    expect(text).toContain('Externo ou sem produção associada');
    expect(text).toContain('1.234567');
    expect(text).toContain('execution-depth-3');
    expect(text).toContain('execution-root-b');
    expect(fixture.nativeElement.querySelectorAll('details').length).toBe(4);
    expect(fixture.nativeElement.querySelector('summary')).not.toBeNull();
  });

  it('should preserve downstream branching', () => {
    const root = batch(batchId, 'Essência', 'EXTERNAL_OR_NON_PRODUCED');
    responses[0].next(
      genealogy('DOWNSTREAM', root, [], [
        edge('execution-a', root, batch('output-a', 'Produto A', 'INTERNALLY_PRODUCED'), 1),
        edge('execution-b', root, batch('output-b', 'Produto B', 'INTERNALLY_PRODUCED'), 2),
      ]),
    );
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Produto A');
    expect(text).toContain('Produto B');
    expect(fixture.nativeElement.querySelectorAll('details').length).toBe(2);
  });

  it('should reload authoritative genealogy when direction changes', () => {
    responses[0].next(genealogy('BOTH', batch(batchId, 'Raiz', 'INTERNALLY_PRODUCED'), [], []));
    fixture.detectChanges();
    responses.push(new Subject<BatchGenealogyDto>());

    const select = fixture.nativeElement.querySelector('select') as HTMLSelectElement;
    select.value = 'UPSTREAM';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(getBatchGenealogy).toHaveBeenLastCalledWith(batchId, 'UPSTREAM');
    expect(fixture.nativeElement.textContent).toContain('Carregando genealogia do lote...');
  });

  it('should render a valid empty genealogy', () => {
    responses[0].next(genealogy('BOTH', batch(batchId, 'Raiz', 'EXTERNAL_OR_NON_PRODUCED'), [], []));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Nenhuma relação de produção encontrada');
  });

  it.each([
    [404, 'BATCH_NOT_FOUND', 'Lote não encontrado.'],
    [500, 'INTERNAL_ERROR', 'Ocorreu um erro no servidor. Tente novamente.'],
  ])('should localize backend error %i without exposing backend text', (status, code, expected) => {
    responses[0].error(apiError(status, code));
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain(expected);
    expect(text).not.toContain('Backend details');
  });
});

function genealogy(
  direction: GenealogyDirection,
  rootBatch: GenealogyBatchDto,
  upstream: readonly GenealogyEdgeDto[],
  downstream: readonly GenealogyEdgeDto[],
): BatchGenealogyDto {
  return { direction, rootBatch, upstream, downstream };
}

function batch(
  batchId: string,
  itemName: string,
  origin: GenealogyBatchDto['origin'],
): GenealogyBatchDto {
  return {
    batchId,
    origin,
    inventoryItemId: `item-${batchId}`,
    itemName,
    itemCategory: 'OTHER',
    unitOfMeasure: 'KILOGRAM',
    supplierId: origin === 'EXTERNAL_OR_NON_PRODUCED' ? 'supplier-1' : null,
    lotCode: `LOT-${batchId}`,
    receivedAt: '2026-09-01',
    expiresAt: null,
  };
}

function edge(
  executionId: string,
  sourceBatch: GenealogyBatchDto,
  outputBatch: GenealogyBatchDto,
  consumedQuantity: number,
  next: readonly GenealogyEdgeDto[] = [],
): GenealogyEdgeDto {
  return {
    executionId,
    formulaId: `formula-${executionId}`,
    productionDate: '2026-09-02',
    completedAt: '2026-09-02T12:00:00Z',
    consumedQuantity,
    sourceBatch,
    outputBatch,
    next,
  };
}

function apiError(status: number, code: string): HttpErrorResponse {
  return new HttpErrorResponse({
    status,
    error: {
      timestamp: '2026-09-05T12:00:00Z',
      status,
      error: status === 404 ? 'Not Found' : 'Internal Server Error',
      code,
      message: 'Backend details',
      path: '/api/v1/production/genealogy/batches/root-batch',
      details: {},
    },
  });
}
