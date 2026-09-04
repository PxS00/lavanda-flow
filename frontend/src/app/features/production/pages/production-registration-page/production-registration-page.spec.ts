import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';

import { InventoryItemDto } from '../../../catalog/data-access/inventory-item.dto';
import { InventoryItemApiService } from '../../../catalog/data-access/inventory-item-api.service';
import { InventoryItemOperationsApiService } from '../../../inventory/data-access/inventory-item-operations-api.service';
import {
  BatchInventoryDto,
  InventoryItemOverviewDto,
} from '../../../inventory/data-access/inventory-operations.dto';
import {
  ProductionExecutionDto,
  RegisterProductionRequest,
} from '../../data-access/production-execution.dto';
import { ProductionExecutionApiService } from '../../data-access/production-execution-api.service';
import { ProductionFormulaDto } from '../../data-access/production-formula.dto';
import { ProductionFormulaApiService } from '../../data-access/production-formula-api.service';
import { ProductionRegistrationPage } from './production-registration-page';

const outputItem = item('output-item', 'Body Splash');
const ingredientItem = item('ingredient-item', 'Essência Lavanda');
const formula: ProductionFormulaDto = {
  id: 'formula-1',
  outputInventoryItemId: outputItem.id,
  outputQuantity: 100,
  outputUnitOfMeasure: 'MILLILITER',
  ingredients: [
    { inventoryItemId: ingredientItem.id, quantity: 5, unitOfMeasure: 'MILLILITER' },
  ],
};
const batches: BatchInventoryDto = {
  inventoryItemId: ingredientItem.id,
  asOfDate: '2026-09-04',
  batches: [
    batch('batch-a', 'ESS-A', 10, 'AVAILABLE'),
    batch('batch-b', 'ESS-B', 8, 'EXPIRED'),
  ],
};
const execution: ProductionExecutionDto = {
  executionId: 'execution-1',
  formulaId: formula.id,
  outputInventoryItemId: outputItem.id,
  outputBatchId: 'output-batch-1',
  outputQuantity: 100,
  lotCode: 'BDS-014-003-09-2026',
  lotCodeMode: 'GENERATED',
  productionDate: '2026-09-04',
  outputReceivedAt: '2026-09-04',
  outputExpiresAt: '2027-09-04',
  completedAt: '2026-09-04T18:00:00Z',
  consumptions: [
    {
      sourceBatchId: 'batch-a',
      sourceInventoryItemId: ingredientItem.id,
      movementId: 'movement-1',
      quantity: 5,
    },
  ],
};

describe('ProductionRegistrationPage', () => {
  let fixture: ComponentFixture<ProductionRegistrationPage>;
  let registerResponse: Subject<ProductionExecutionDto>;
  let register: ReturnType<
    typeof vi.fn<(request: RegisterProductionRequest) => Observable<ProductionExecutionDto>>
  >;
  let getOverview: ReturnType<
    typeof vi.fn<(inventoryItemId: string) => Observable<InventoryItemOverviewDto>>
  >;

  async function configure(
    formulas: readonly ProductionFormulaDto[] = [formula],
    overviewFactory: (inventoryItemId: string) => Observable<InventoryItemOverviewDto> = (id) =>
      of(overview(id)),
  ): Promise<void> {
    registerResponse = new Subject<ProductionExecutionDto>();
    register = vi.fn(() => registerResponse);
    getOverview = vi.fn((inventoryItemId) => overviewFactory(inventoryItemId));

    await TestBed.configureTestingModule({
      imports: [ProductionRegistrationPage],
      providers: [
        provideRouter([]),
        { provide: ProductionFormulaApiService, useValue: { list: vi.fn(() => of(formulas)) } },
        {
          provide: InventoryItemApiService,
          useValue: {
            getById: vi.fn((inventoryItemId: string) =>
              of(inventoryItemId === outputItem.id ? outputItem : ingredientItem),
            ),
          },
        },
        {
          provide: InventoryItemOperationsApiService,
          useValue: {
            getBatches: vi.fn(() => of(batches)),
            getOverview,
          },
        },
        { provide: ProductionExecutionApiService, useValue: { register } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProductionRegistrationPage);
    fixture.detectChanges();
  }

  it('should render an unavailable state when no formula exists', async () => {
    await configure([]);

    expect(fixture.nativeElement.textContent).toContain('Nenhuma fórmula disponível');
    expect(register).not.toHaveBeenCalled();
  });

  it('should review generated production before sending the backend request', async () => {
    await configure();
    fillBaseForm();

    reviewProduction();

    expect(register).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Revisar produção');
    expect(fixture.nativeElement.textContent).toContain('definido pelo servidor ao concluir');
    expect(fixture.nativeElement.textContent).not.toContain(execution.lotCode);

    clickButton('Confirmar produção');
    expect(register).toHaveBeenCalledWith({
      formulaId: formula.id,
      outputQuantity: 100,
      sourceAllocations: [{ batchId: 'batch-a', quantity: 5 }],
      productionDate: '2026-09-04',
      outputReceivedAt: '2026-09-04',
      outputExpiresAt: '2027-09-04',
      lotCodeMode: 'GENERATED',
      manualLotCode: null,
    });
  });

  it('should submit manual lot mode without changing its wire value', async () => {
    await configure();
    fillBaseForm();
    fixture.componentInstance.registrationForm.controls.lotCodeMode.setValue('MANUAL');
    fixture.componentInstance.registrationForm.controls.manualLotCode.setValue('MANUAL-001');
    fixture.detectChanges();

    reviewProduction();
    clickButton('Confirmar produção');

    expect(register).toHaveBeenCalledWith(
      expect.objectContaining({ lotCodeMode: 'MANUAL', manualLotCode: 'MANUAL-001' }),
    );
  });

  it('should flatten multiple concrete batches for one ingredient', async () => {
    await configure();
    fillBaseForm();
    clickButton('Adicionar lote');
    const allocations =
      fixture.componentInstance.registrationForm.controls.allocationGroups.at(0).controls.allocations;
    allocations.at(1).setValue({ batchId: 'batch-b', quantity: '1.25' });
    fixture.detectChanges();

    reviewProduction();
    clickButton('Confirmar produção');

    expect(register).toHaveBeenCalledWith(
      expect.objectContaining({
        sourceAllocations: [
          { batchId: 'batch-a', quantity: 5 },
          { batchId: 'batch-b', quantity: 1.25 },
        ],
      }),
    );
  });

  it('should prevent duplicate confirmation while the production request is in flight', async () => {
    await configure();
    fillBaseForm();
    reviewProduction();

    clickButton('Confirmar produção');
    expect(button('Registrando...').disabled).toBe(true);
    button('Registrando...').click();
    fixture.detectChanges();

    expect(register).toHaveBeenCalledTimes(1);
    expect(getOverview).not.toHaveBeenCalled();
  });

  it.each([
    ['INSUFFICIENT_STOCK', 'Um dos lotes selecionados não possui saldo suficiente.'],
    ['EXPIRED_BATCH', 'Um dos lotes selecionados está vencido e não pode ser consumido.'],
    [
      'INVALID_PRODUCTION_ALLOCATION',
      'As quantidades dos lotes não correspondem aos requisitos da fórmula.',
    ],
  ])('should preserve input and show backend rejection %s', async (code, expectedMessage) => {
    await configure();
    fillBaseForm();
    reviewProduction();
    clickButton('Confirmar produção');

    registerResponse.error(apiError(code));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(expectedMessage);
    expect(fixture.componentInstance.registrationForm.controls.outputQuantity.value).toBe('100');
    expect(fixture.nativeElement.textContent).toContain('Revisar produção');
  });

  it('should show backend-confirmed result and refresh affected inventory only after success', async () => {
    await configure();
    fillBaseForm();
    reviewProduction();
    clickButton('Confirmar produção');

    expect(getOverview).not.toHaveBeenCalled();
    registerResponse.next(execution);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Produção registrada');
    expect(fixture.nativeElement.textContent).toContain(execution.lotCode);
    expect(fixture.nativeElement.textContent).toContain(execution.outputBatchId);
    expect(getOverview).toHaveBeenCalledWith(outputItem.id);
    expect(getOverview).toHaveBeenCalledWith(ingredientItem.id);
  });

  it('should keep production successful when post-success inventory refresh fails', async () => {
    await configure([formula], () =>
      throwError(() => new HttpErrorResponse({ status: 0, statusText: 'Network error' })),
    );
    fillBaseForm();
    reviewProduction();
    clickButton('Confirmar produção');
    registerResponse.next(execution);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Produção registrada');
    expect(fixture.nativeElement.textContent).toContain(execution.lotCode);
    expect(fixture.nativeElement.textContent).toContain(
      'Produção concluída; atualização do estoque pendente',
    );
    expect(register).toHaveBeenCalledTimes(1);
    expect(getOverview).toHaveBeenCalledTimes(2);

    clickButton('Tentar atualizar estoque novamente');

    expect(register).toHaveBeenCalledTimes(1);
    expect(getOverview).toHaveBeenCalledTimes(4);
  });

  function fillBaseForm(): void {
    const form = fixture.componentInstance.registrationForm;
    form.controls.formulaId.setValue(formula.id);
    fixture.detectChanges();
    form.controls.outputQuantity.setValue('100');
    form.controls.productionDate.setValue('2026-09-04');
    form.controls.outputReceivedAt.setValue('2026-09-04');
    form.controls.outputExpiresAt.setValue('2027-09-04');
    form.controls.allocationGroups.at(0).controls.allocations.at(0).setValue({
      batchId: 'batch-a',
      quantity: '5',
    });
    fixture.detectChanges();
  }

  function reviewProduction(): void {
    (fixture.nativeElement.querySelector('form') as HTMLFormElement).dispatchEvent(
      new SubmitEvent('submit', { bubbles: true, cancelable: true }),
    );
    fixture.detectChanges();
  }

  function clickButton(text: string): void {
    button(text).click();
    fixture.detectChanges();
  }

  function button(text: string): HTMLButtonElement {
    return Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>,
    ).find((candidate) => candidate.textContent?.includes(text)) as HTMLButtonElement;
  }
});

function item(id: string, name: string): InventoryItemDto {
  return {
    id,
    name,
    description: null,
    category: 'OTHER',
    unitOfMeasure: 'MILLILITER',
    active: true,
    essenceReference: null,
    productionTypeCode: null,
  };
}

function batch(
  batchId: string,
  lotCode: string,
  currentQuantity: number,
  status: 'AVAILABLE' | 'EXPIRED' | 'ZERO_BALANCE',
) {
  return {
    batchId,
    inventoryItemId: ingredientItem.id,
    supplierId: 'supplier-1',
    lotCode,
    initialQuantity: currentQuantity,
    currentQuantity,
    receivedAt: '2026-08-01',
    expiresAt: status === 'EXPIRED' ? '2026-09-01' : '2027-09-01',
    status,
  } as const;
}

function overview(inventoryItemId: string): InventoryItemOverviewDto {
  return {
    inventoryItemId,
    name: inventoryItemId === outputItem.id ? outputItem.name : ingredientItem.name,
    category: 'OTHER',
    unitOfMeasure: 'MILLILITER',
    active: true,
    asOfDate: '2026-09-04',
    expirationWindowDays: 30,
    totalCurrentQuantity: 95,
    availableQuantity: 95,
    minimumQuantity: null,
    lowStock: false,
    outOfStock: false,
    nonZeroBatchCount: 1,
    nearestExpiration: '2027-09-01',
    expiredBatchCount: 0,
    expiringSoonBatchCount: 0,
  };
}

function apiError(code: string): HttpErrorResponse {
  return new HttpErrorResponse({
    status: 422,
    error: {
      timestamp: '2026-09-04T18:00:00Z',
      status: 422,
      error: 'Unprocessable Entity',
      code,
      message: 'Production registration rejected',
      path: '/api/v1/production/executions',
      details: {},
    },
  });
}
