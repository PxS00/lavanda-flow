import {HttpErrorResponse} from '@angular/common/http';
import {Component, input, output} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MatPaginatorIntl} from '@angular/material/paginator';
import {By} from '@angular/platform-browser';
import {ActivatedRoute, convertToParamMap, ParamMap, provideRouter} from '@angular/router';
import {Observable, Subject} from 'rxjs';

import {InventoryItemOperationsApiService} from '../../data-access/inventory-item-operations-api.service';
import {
  BatchInventoryDto,
  ConfigureMinimumStockLevelRequest,
  InventoryItemMovementHistoryQuery,
  InventoryItemOverviewDto,
  InventoryUnitOfMeasure,
  MinimumStockLevelDto,
  MovementHistoryPageDto,
} from '../../data-access/inventory-operations.dto';
import {MovementHistoryApiService} from '../../data-access/movement-history-api.service';
import {FefoWithdrawalPanel} from '../../ui/fefo-withdrawal-panel/fefo-withdrawal-panel';
import {createPtBrPaginatorIntl} from '../../../../core/i18n/pt-br-paginator-intl';
import {InventoryItemOperationalPage} from './inventory-item-operational-page';

@Component({ selector: 'app-fefo-withdrawal-panel', template: 'withdrawal panel' })
class FefoWithdrawalPanelStub {
  readonly inventoryItemId = input.required<string>();
  readonly itemName = input.required<string>();
  readonly unitOfMeasure = input.required<InventoryUnitOfMeasure>();
  readonly availableQuantity = input.required<number>();
  readonly active = input.required<boolean>();
  readonly withdrawalCompleted = output<void>();
}

describe('InventoryItemOperationalPage', () => {
  const inventoryItemId = 'bd194732-51cf-4f73-bc5d-3a9f9337adcc';
  const overview: InventoryItemOverviewDto = {
    inventoryItemId,
    name: 'Lavender Essence',
    category: 'ESSENCE',
    unitOfMeasure: 'MILLILITER',
    active: true,
    asOfDate: '2026-09-01',
    expirationWindowDays: 30,
    totalCurrentQuantity: 120,
    availableQuantity: 80,
    minimumQuantity: 90,
    lowStock: true,
    outOfStock: false,
    nonZeroBatchCount: 3,
    nearestExpiration: '2026-09-20',
    expiredBatchCount: 1,
    expiringSoonBatchCount: 2,
  };
  const minimum: MinimumStockLevelDto = { inventoryItemId, minimumQuantity: 90 };
  const populatedMovements: MovementHistoryPageDto = {
    content: [
      {
        movementId: 'movement-1',
        inventoryItemId,
        inventoryItemName: 'Lavender Essence',
        unitOfMeasure: 'MILLILITER',
        inventoryItemActive: true,
        batchId: 'batch-1',
        lotCode: 'LOT-A',
        type: 'CONSUMPTION',
        quantity: 5.25,
        reason: 'Perfume production',
        occurredAt: '2026-09-01T15:00:00Z',
      },
    ],
    page: 0,
    size: 20,
    totalElements: 21,
    totalPages: 2,
  };

  let fixture: ComponentFixture<InventoryItemOperationalPage>;
  let routeParams: Subject<ParamMap>;
  let overviewResponse: Subject<InventoryItemOverviewDto>;
  let batchResponse: Subject<BatchInventoryDto>;
  let minimumResponse: Subject<MinimumStockLevelDto>;
  let movementResponse: Subject<MovementHistoryPageDto>;
  let configureResponse: Subject<MinimumStockLevelDto>;
  let deleteResponse: Subject<void>;
  let getOverview: ReturnType<typeof vi.fn<(id: string) => Observable<InventoryItemOverviewDto>>>;
  let getBatches: ReturnType<typeof vi.fn<(id: string) => Observable<BatchInventoryDto>>>;
  let getMinimumStockLevel: ReturnType<
    typeof vi.fn<(id: string) => Observable<MinimumStockLevelDto>>
  >;
  let configureMinimumStockLevel: ReturnType<
    typeof vi.fn<
      (id: string, request: ConfigureMinimumStockLevelRequest) => Observable<MinimumStockLevelDto>
    >
  >;
  let deleteMinimumStockLevel: ReturnType<typeof vi.fn<(id: string) => Observable<void>>>;
  let searchMovements: ReturnType<
    typeof vi.fn<(query: InventoryItemMovementHistoryQuery) => Observable<MovementHistoryPageDto>>
  >;

  beforeEach(async () => {
    routeParams = new Subject<ParamMap>();
    overviewResponse = new Subject<InventoryItemOverviewDto>();
    batchResponse = new Subject<BatchInventoryDto>();
    minimumResponse = new Subject<MinimumStockLevelDto>();
    movementResponse = new Subject<MovementHistoryPageDto>();
    configureResponse = new Subject<MinimumStockLevelDto>();
    deleteResponse = new Subject<void>();

    getOverview = vi.fn(() => overviewResponse);
    getBatches = vi.fn(() => batchResponse);
    getMinimumStockLevel = vi.fn(() => minimumResponse);
    configureMinimumStockLevel = vi.fn(() => configureResponse);
    deleteMinimumStockLevel = vi.fn(() => deleteResponse);
    searchMovements = vi.fn(() => movementResponse);

    TestBed.configureTestingModule({
      imports: [InventoryItemOperationalPage],
      providers: [
        { provide: MatPaginatorIntl, useFactory: createPtBrPaginatorIntl },
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap: routeParams } },
        {
          provide: InventoryItemOperationsApiService,
          useValue: {
            getOverview,
            getBatches,
            getMinimumStockLevel,
            configureMinimumStockLevel,
            deleteMinimumStockLevel,
          },
        },
        { provide: MovementHistoryApiService, useValue: { search: searchMovements } },
      ],
    });

    TestBed.overrideComponent(InventoryItemOperationalPage, {
      remove: { imports: [FefoWithdrawalPanel] },
      add: { imports: [FefoWithdrawalPanelStub] },
    });

    await TestBed.compileComponents();

    fixture = TestBed.createComponent(InventoryItemOperationalPage);
    fixture.detectChanges();
    routeParams.next(convertToParamMap({ inventoryItemId }));
    fixture.detectChanges();
  });

  it('should request every independent panel for the direct route item', () => {
    expect(getOverview).toHaveBeenCalledWith(inventoryItemId);
    expect(getBatches).toHaveBeenCalledWith(inventoryItemId);
    expect(getMinimumStockLevel).toHaveBeenCalledWith(inventoryItemId);
    expect(searchMovements).toHaveBeenCalledWith({ inventoryItemId, page: 0, size: 20 });
    expect(fixture.nativeElement.textContent).toContain('Carregando visão geral do estoque...');
    expect(fixture.nativeElement.textContent).toContain('Carregando lotes...');
    expect(fixture.nativeElement.textContent).toContain('Carregando histórico de movimentações...');
  });

  it('should render backend overview semantics without conflating physical and available stock', () => {
    overviewResponse.next(overview);
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Lavender Essence');
    expect(text).toContain('Quantidade física atual');
    expect(text).toContain('120');
    expect(text).toContain('Estoque disponível');
    expect(text).toContain('80');
    expect(text).toContain('Estoque baixo');
    expect(text).toContain('20/09/2026');
    expect(text).toContain('janela de validade de 30 dias');
  });

  it('should pass concrete active item context to the withdrawal panel and refresh authoritative panels after completion', () => {
    overviewResponse.next(overview);
    fixture.detectChanges();

    const panel = fixture.debugElement.query(By.directive(FefoWithdrawalPanelStub))
      .componentInstance as FefoWithdrawalPanelStub;
    expect(panel.inventoryItemId()).toBe(inventoryItemId);
    expect(panel.itemName()).toBe('Lavender Essence');
    expect(panel.unitOfMeasure()).toBe('MILLILITER');
    expect(panel.availableQuantity()).toBe(80);
    expect(panel.active()).toBe(true);

    panel.withdrawalCompleted.emit();
    fixture.detectChanges();

    expect(getOverview).toHaveBeenCalledTimes(2);
    expect(getBatches).toHaveBeenCalledTimes(2);
    expect(searchMovements).toHaveBeenCalledTimes(2);
    expect(getMinimumStockLevel).toHaveBeenCalledTimes(1);
    expect(fixture.debugElement.query(By.directive(FefoWithdrawalPanelStub))).not.toBeNull();
  });

  it('should present an inactive item to the withdrawal panel without enabling a new withdrawal in the parent', () => {
    overviewResponse.next({ ...overview, active: false });
    fixture.detectChanges();

    const panel = fixture.debugElement.query(By.directive(FefoWithdrawalPanelStub))
      .componentInstance as FefoWithdrawalPanelStub;
    expect(panel.active()).toBe(false);
  });

  it('should clear item A withdrawal context before item B loads and ignore a stale item A overview', () => {
    const itemBId = '5184d508-35eb-42de-a2a0-44c4f6c9b9ae';
    const itemBOverviewResponse = new Subject<InventoryItemOverviewDto>();
    overviewResponse.next(overview);
    fixture.detectChanges();

    expect(
      (fixture.debugElement.query(By.directive(FefoWithdrawalPanelStub))
        .componentInstance as FefoWithdrawalPanelStub).inventoryItemId(),
    ).toBe(inventoryItemId);

    getOverview.mockImplementation((id) => (id === itemBId ? itemBOverviewResponse : overviewResponse));
    routeParams.next(convertToParamMap({ inventoryItemId: itemBId }));
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.directive(FefoWithdrawalPanelStub))).toBeNull();

    overviewResponse.next(overview);
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.directive(FefoWithdrawalPanelStub))).toBeNull();

    itemBOverviewResponse.next({
      ...overview,
      inventoryItemId: itemBId,
      name: 'Lavender Base',
      availableQuantity: 45,
    });
    fixture.detectChanges();

    const panel = fixture.debugElement.query(By.directive(FefoWithdrawalPanelStub))
      .componentInstance as FefoWithdrawalPanelStub;
    expect(panel.inventoryItemId()).toBe(itemBId);
    expect(panel.itemName()).toBe('Lavender Base');
    expect(panel.availableQuantity()).toBe(45);
  });

  it('should preserve backend batch ordering and display every operational status', () => {
    batchResponse.next({
      inventoryItemId,
      asOfDate: '2026-09-01',
      batches: [
        batch('batch-a', 'LOT-A', 'AVAILABLE'),
        batch('batch-b', 'LOT-B', 'EXPIRED'),
        batch('batch-c', 'LOT-C', 'ZERO_BALANCE'),
      ],
    });
    fixture.detectChanges();

    const rows = Array.from(fixture.nativeElement.querySelectorAll('tbody tr')) as HTMLTableRowElement[];
    expect(rows.map((row) => row.textContent)).toEqual([
      expect.stringContaining('LOT-A'),
      expect.stringContaining('LOT-B'),
      expect.stringContaining('LOT-C'),
    ]);
    expect(rows[0].textContent).toContain('Disponível');
    expect(rows[1].textContent).toContain('Vencido');
    expect(rows[2].textContent).toContain('Saldo zerado');
    expect(rows[0].textContent).toContain('supplier-1');
    expect(rows[0].textContent).toContain('100');
    expect(rows[0].textContent).toContain('40');
  });

  it('should treat only the dedicated minimum-not-found code as an unconfigured state', () => {
    minimumResponse.error(
      apiError(404, 'MINIMUM_STOCK_LEVEL_NOT_FOUND', 'Minimum stock level not found.'),
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Nenhum estoque mínimo configurado.');
    expect(fixture.nativeElement.textContent).not.toContain('Minimum stock level not found.');
  });

  it('should reject invalid minimum values and save a positive six-decimal value', () => {
    minimumResponse.error(
      apiError(404, 'MINIMUM_STOCK_LEVEL_NOT_FOUND', 'Minimum stock level not found.'),
    );
    fixture.componentInstance.minimumStockModel.set({ minimumQuantity: '-1' });
    fixture.detectChanges();
    submitMinimumForm();

    expect(configureMinimumStockLevel).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain(
      'Use um número positivo com até 6 casas decimais.',
    );

    fixture.componentInstance.minimumStockModel.set({ minimumQuantity: '25.123456' });
    fixture.detectChanges();
    submitMinimumForm();

    expect(configureMinimumStockLevel).toHaveBeenCalledWith(inventoryItemId, {
      minimumQuantity: 25.123456,
    });

    configureResponse.next({ inventoryItemId, minimumQuantity: 25.123456 });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Estoque mínimo salvo.');
    expect(getOverview).toHaveBeenCalledTimes(2);
  });

  it('should require explicit confirmation before removing a minimum stock level', () => {
    minimumResponse.next(minimum);
    fixture.detectChanges();

    clickButton('Remover mínimo');
    expect(deleteMinimumStockLevel).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Remover este estoque mínimo?');

    clickButton('Confirmar remoção');
    expect(deleteMinimumStockLevel).toHaveBeenCalledWith(inventoryItemId);

    deleteResponse.next(undefined);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Estoque mínimo removido.');
    expect(fixture.nativeElement.textContent).toContain('Nenhum estoque mínimo configurado.');
    expect(getOverview).toHaveBeenCalledTimes(2);
  });

  it('should render movement audit data and request the selected zero-based page', () => {
    movementResponse.next(populatedMovements);
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Consumo');
    expect(text).toContain('5.25');
    expect(text).toContain('LOT-A');
    expect(text).toContain('Perfume production');
    expect(text).toContain(
      formatDate(populatedMovements.content[0].occurredAt, 'dd/MM/yyyy, HH:mm', 'pt-BR'),
    );

    const nextButton = fixture.nativeElement.querySelector(
      'button[aria-label="Próxima página"]',
    ) as HTMLButtonElement;
    nextButton.click();
    fixture.detectChanges();

    expect(searchMovements).toHaveBeenLastCalledWith({ inventoryItemId, page: 1, size: 20 });
  });

  it('should render independent empty states for batches and movement history', () => {
    batchResponse.next({ inventoryItemId, asOfDate: '2026-09-01', batches: [] });
    movementResponse.next({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Nenhum lote encontrado');
    expect(fixture.nativeElement.textContent).toContain('Nenhuma movimentação encontrada');
  });

  it('should keep usable overview data visible when a secondary panel fails', () => {
    overviewResponse.next(overview);
    batchResponse.error(apiError(500, 'INTERNAL_ERROR', 'Internal details'));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Lavender Essence');
    expect(fixture.nativeElement.textContent).toContain(
      'Ocorreu um erro no servidor. Tente novamente.',
    );
  });

  function batch(
    batchId: string,
    lotCode: string,
    status: 'AVAILABLE' | 'EXPIRED' | 'ZERO_BALANCE',
  ) {
    return {
      batchId,
      inventoryItemId,
      supplierId: 'supplier-1',
      lotCode,
      initialQuantity: 100,
      currentQuantity: status === 'ZERO_BALANCE' ? 0 : 40,
      receivedAt: '2026-08-01',
      expiresAt: status === 'AVAILABLE' ? '2026-10-01' : '2026-08-31',
      status,
    } as const;
  }

  function submitMinimumForm(): void {
    const form = fixture.nativeElement.querySelector('.minimum-form') as HTMLFormElement;
    form.dispatchEvent(new SubmitEvent('submit', { bubbles: true, cancelable: true }));
    fixture.detectChanges();
  }

  function clickButton(label: string): void {
    const button = Array.from(fixture.nativeElement.querySelectorAll('button')).find(
      (candidate) => (candidate as HTMLButtonElement).textContent?.trim() === label,
    ) as HTMLButtonElement | undefined;
    expect(button).toBeDefined();
    button?.click();
    fixture.detectChanges();
  }

  function apiError(status: number, code: string, message: string): HttpErrorResponse {
    return new HttpErrorResponse({
      status,
      error: {
        timestamp: '2026-09-01T12:00:00Z',
        status,
        error: status === 404 ? 'Not Found' : 'Internal Server Error',
        code,
        message,
        path: `/api/v1/inventory/items/${inventoryItemId}`,
        details: {},
      },
    });
  }
});
