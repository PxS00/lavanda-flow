import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, Subject } from 'rxjs';

import { ProductionExecutionApiService } from '../../data-access/production-execution-api.service';
import { ProductionExecutionDetailsDto } from '../../data-access/production-execution.dto';
import { ProductionExecutionDetailPage } from './production-execution-detail-page';

describe('ProductionExecutionDetailPage', () => {
  let fixture: ComponentFixture<ProductionExecutionDetailPage>;
  let response: Subject<ProductionExecutionDetailsDto>;
  let getById: ReturnType<typeof vi.fn>;

  const detail: ProductionExecutionDetailsDto = {
    executionId: 'execution-1',
    formulaId: 'formula-1',
    outputInventoryItemId: 'output-item',
    outputItemName: 'Sabonete líquido',
    outputUnitOfMeasure: 'MILLILITER',
    outputBatchId: 'output-batch',
    outputQuantity: '1000.500000',
    lotCode: 'OUT-1',
    lotCodeMode: 'GENERATED',
    productionDate: '2026-09-18',
    outputReceivedAt: '2026-09-18',
    outputExpiresAt: null,
    completedAt: '2026-09-18T15:00:00Z',
    consumptions: [
      {
        sourceBatchId: 'source-batch-2',
        sourceInventoryItemId: 'source-item-2',
        sourceItemName: 'Base',
        sourceUnitOfMeasure: 'LITER',
        sourceLotCode: null,
        movementId: 'movement-2',
        quantity: '2.000002',
      },
      {
        sourceBatchId: 'source-batch-1',
        sourceInventoryItemId: 'source-item-1',
        sourceItemName: 'Essência',
        sourceUnitOfMeasure: 'MILLILITER',
        sourceLotCode: 'SRC-1',
        movementId: 'movement-1',
        quantity: '1.000001',
      },
    ],
  };

  beforeEach(async () => {
    response = new Subject<ProductionExecutionDetailsDto>();
    getById = vi.fn(() => response);
    await TestBed.configureTestingModule({
      imports: [ProductionExecutionDetailPage],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { paramMap: of(convertToParamMap({ executionId: 'execution-1' })) },
        },
        { provide: ProductionExecutionApiService, useValue: { getById } },
      ],
    }).compileComponents();
  });

  it('should fetch the route identity directly and render persisted detail in source order', () => {
    fixture = TestBed.createComponent(ProductionExecutionDetailPage);
    fixture.detectChanges();
    expect(getById).toHaveBeenCalledWith('execution-1');
    expect(fixture.nativeElement.textContent).toContain('Carregando detalhes da produção');

    response.next(detail);
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('1.000,5 Mililitro');
    expect(text).toContain('Gerado pelo sistema');
    expect(text).toContain('Não informada');
    expect(text.indexOf('Base')).toBeLessThan(text.indexOf('Essência'));
    expect(text).toContain('2,000002 Litro');
    expect(text).toContain('Não informado');
    expect(text).toContain('1,000001 Mililitro');
    expect(text).toContain('movement-1');

    const links = Array.from(fixture.nativeElement.querySelectorAll('a')) as HTMLAnchorElement[];
    expect(links.map((link) => [link.textContent?.trim(), link.getAttribute('href')])).toEqual(
      expect.arrayContaining([
        ['Abrir fórmula atual', '/production/formulas/formula-1'],
        ['Abrir estoque do lote', '/inventory/items/output-item?batchId=output-batch#batches'],
        ['Ver genealogia', '/production/genealogy/batches/output-batch'],
        ['Abrir estoque do lote', '/inventory/items/source-item-2?batchId=source-batch-2#batches'],
        ['Ver genealogia', '/production/genealogy/batches/source-batch-2'],
      ]),
    );
  });

  it('should show an error state and retry the same route identity', () => {
    fixture = TestBed.createComponent(ProductionExecutionDetailPage);
    fixture.detectChanges();
    response.error(new Error('not found'));
    fixture.detectChanges();

    const buttons = Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>,
    );
    const retry = buttons.find(
      (button) => button.textContent?.trim() === 'Tentar novamente',
    ) as HTMLButtonElement;
    retry.click();
    expect(getById).toHaveBeenCalledTimes(2);
    expect(getById).toHaveBeenLastCalledWith('execution-1');
  });
});
