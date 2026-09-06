import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { routes } from './app.routes';
import { API_BASE_URL } from './core/config/api-base-url.token';
import { InventoryItemDto } from './features/catalog/data-access/inventory-item.dto';
import { ProductionExecutionDto } from './features/production/data-access/production-execution.dto';
import { ProductionFormulaDto } from './features/production/data-access/production-formula.dto';
import { BatchGenealogyDto } from './features/production/data-access/production-genealogy.dto';
import { ProductionRegistrationPage } from './features/production/pages/production-registration-page/production-registration-page';

describe('V1 operational readiness', () => {
  const apiUrl = 'https://api.example.test/api/v1';
  const sourceItem = item('source-item', 'Base intermediária', 'BAS');
  const outputItem = item('output-item', 'Produto final', 'BDS');
  const formula: ProductionFormulaDto = {
    id: 'formula-final',
    outputInventoryItemId: outputItem.id,
    outputQuantity: 10,
    outputUnitOfMeasure: 'MILLILITER',
    ingredients: [
      { inventoryItemId: sourceItem.id, quantity: 4.25, unitOfMeasure: 'MILLILITER' },
    ],
  };
  const execution: ProductionExecutionDto = {
    executionId: 'execution-final',
    formulaId: formula.id,
    outputInventoryItemId: outputItem.id,
    outputBatchId: 'final-output-batch',
    outputQuantity: 10,
    lotCode: 'BDS-000-007-09-2026',
    lotCodeMode: 'GENERATED',
    productionDate: '2026-09-30',
    outputReceivedAt: '2026-09-30',
    outputExpiresAt: null,
    completedAt: '2026-09-30T15:00:00Z',
    consumptions: [
      {
        sourceBatchId: 'intermediate-batch',
        sourceInventoryItemId: sourceItem.id,
        movementId: 'consumption-final',
        quantity: 4.25,
      },
    ],
  };
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: apiUrl },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('composes dashboard, production setup, backend-confirmed registration, and genealogy', async () => {
    const harness = await RouterTestingHarness.create('/dashboard');
    http.expectOne(`${apiUrl}/inventory/dashboard`).flush({
      asOfDate: '2026-09-30',
      expirationWindowDays: 31,
      activeItemCount: 5,
      lowStockItemCount: 1,
      outOfStockItemCount: 2,
      expiringSoonBatchCount: 1,
      expiredBatchCount: 1,
    });
    harness.fixture.detectChanges();
    expect(text(harness)).toContain('Painel operacional');
    expect(text(harness)).toContain('Lotes próximos do vencimento');

    findLink(harness, 'Produção').click();
    await harness.fixture.whenStable();
    http.expectOne(`${apiUrl}/production/formulas`).flush([formula]);
    http.expectOne(`${apiUrl}/inventory-items/${outputItem.id}`).flush(outputItem);
    harness.fixture.detectChanges();
    expect(text(harness)).toContain('Fórmulas de produção');
    expect(text(harness)).toContain(outputItem.name);

    findLink(harness, 'Registrar produção').click();
    await harness.fixture.whenStable();
    http.expectOne(`${apiUrl}/production/formulas`).flush([formula]);
    http.expectOne(`${apiUrl}/inventory-items/${outputItem.id}`).flush(outputItem);
    harness.fixture.detectChanges();

    const page = harness.fixture.debugElement.query(By.directive(ProductionRegistrationPage))
      .componentInstance as ProductionRegistrationPage;
    page.registrationForm.controls.formulaId.setValue(formula.id);
    harness.fixture.detectChanges();
    http.expectOne(`${apiUrl}/inventory-items/${sourceItem.id}`).flush(sourceItem);
    http.expectOne(`${apiUrl}/inventory/items/${sourceItem.id}/batches`).flush({
      inventoryItemId: sourceItem.id,
      asOfDate: '2026-09-30',
      batches: [
        {
          batchId: 'intermediate-batch',
          inventoryItemId: sourceItem.id,
          supplierId: null,
          lotCode: 'BAS-000-003-09-2026',
          initialQuantity: 10,
          currentQuantity: 10,
          receivedAt: '2026-09-30',
          expiresAt: null,
          status: 'AVAILABLE',
        },
      ],
    });
    harness.fixture.detectChanges();

    page.registrationForm.controls.outputQuantity.setValue('10');
    page.registrationForm.controls.productionDate.setValue('2026-09-30');
    page.registrationForm.controls.outputReceivedAt.setValue('2026-09-30');
    page.registrationForm.controls.allocationGroups.at(0).controls.allocations.at(0).setValue({
      batchId: 'intermediate-batch',
      quantity: '4.25',
    });
    harness.fixture.detectChanges();
    (harness.routeNativeElement?.querySelector('form') as HTMLFormElement).dispatchEvent(
      new SubmitEvent('submit', { bubbles: true, cancelable: true }),
    );
    harness.fixture.detectChanges();

    expect(text(harness)).toContain('Revisar produção');
    expect(text(harness)).toContain('definido pelo servidor ao concluir');
    expect(text(harness)).not.toContain(execution.lotCode);
    findButton(harness, 'Confirmar produção').click();
    harness.fixture.detectChanges();

    const registration = http.expectOne(`${apiUrl}/production/executions`);
    expect(registration.request.method).toBe('POST');
    expect(registration.request.body).toEqual({
      formulaId: formula.id,
      outputQuantity: 10,
      sourceAllocations: [{ batchId: 'intermediate-batch', quantity: 4.25 }],
      productionDate: '2026-09-30',
      outputReceivedAt: '2026-09-30',
      outputExpiresAt: null,
      lotCodeMode: 'GENERATED',
      manualLotCode: null,
    });
    expect(text(harness)).not.toContain('Produção registrada');

    registration.flush(execution);
    http.expectOne(`${apiUrl}/inventory/items/${outputItem.id}/overview`).flush(overview(outputItem, 10));
    http.expectOne(`${apiUrl}/inventory/items/${sourceItem.id}/overview`).flush(overview(sourceItem, 5.75));
    harness.fixture.detectChanges();

    expect(text(harness)).toContain('Produção registrada');
    expect(text(harness)).toContain(execution.lotCode);
    expect(text(harness)).toContain(execution.outputBatchId);

    await TestBed.inject(Router).navigateByUrl(
      `/production/genealogy/batches/${execution.outputBatchId}`,
    );
    await harness.fixture.whenStable();
    http.expectOne(
      `${apiUrl}/production/genealogy/batches/${execution.outputBatchId}?direction=BOTH`,
    ).flush(genealogy());
    harness.fixture.detectChanges();

    expect(text(harness)).toContain('Genealogia do lote');
    expect(text(harness)).toContain('Origem / lotes anteriores');
    expect(text(harness)).toContain('Uso / lotes posteriores');
    expect(text(harness)).toContain('Fonte externa original');
    expect(text(harness)).toContain('Base intermediária confirmada');
    expect(text(harness)).toContain('Descendente confirmado pelo backend');
    expect(text(harness)).toContain('execution-source-to-intermediate');
    expect(text(harness)).toContain('4.25');
  });

  function genealogy(): BatchGenealogyDto {
    const original = genealogyBatch('original-batch', 'Fonte externa original', 'EXTERNAL_OR_NON_PRODUCED');
    const intermediate = genealogyBatch(
      'intermediate-batch',
      'Base intermediária confirmada',
      'INTERNALLY_PRODUCED',
    );
    const final = genealogyBatch(
      execution.outputBatchId,
      'Produto final confirmado',
      'INTERNALLY_PRODUCED',
    );
    const descendant = genealogyBatch(
      'descendant-batch',
      'Descendente confirmado pelo backend',
      'INTERNALLY_PRODUCED',
    );
    return {
      direction: 'BOTH',
      rootBatch: final,
      upstream: [
        {
          executionId: execution.executionId,
          formulaId: formula.id,
          productionDate: '2026-09-30',
          completedAt: '2026-09-30T15:00:00Z',
          consumedQuantity: 4.25,
          sourceBatch: intermediate,
          outputBatch: final,
          next: [
            {
              executionId: 'execution-source-to-intermediate',
              formulaId: 'formula-intermediate',
              productionDate: '2026-09-29',
              completedAt: '2026-09-29T15:00:00Z',
              consumedQuantity: 6.5,
              sourceBatch: original,
              outputBatch: intermediate,
              next: [],
            },
          ],
        },
      ],
      downstream: [
        {
          executionId: 'execution-descendant',
          formulaId: 'formula-descendant',
          productionDate: '2026-10-01',
          completedAt: '2026-10-01T15:00:00Z',
          consumedQuantity: 2,
          sourceBatch: final,
          outputBatch: descendant,
          next: [],
        },
      ],
    };
  }

  function text(harness: RouterTestingHarness): string {
    return harness.routeNativeElement?.textContent ?? '';
  }
});

function item(id: string, name: string, productionTypeCode: string): InventoryItemDto {
  return {
    id,
    name,
    description: null,
    category: 'OTHER',
    unitOfMeasure: 'MILLILITER',
    active: true,
    essenceReference: null,
    productionTypeCode,
  };
}

function overview(inventoryItem: InventoryItemDto, quantity: number) {
  return {
    inventoryItemId: inventoryItem.id,
    name: inventoryItem.name,
    category: inventoryItem.category,
    unitOfMeasure: inventoryItem.unitOfMeasure,
    active: true,
    asOfDate: '2026-09-30',
    expirationWindowDays: 31,
    totalCurrentQuantity: quantity,
    availableQuantity: quantity,
    minimumQuantity: null,
    lowStock: false,
    outOfStock: false,
    nonZeroBatchCount: 1,
    nearestExpiration: null,
    expiredBatchCount: 0,
    expiringSoonBatchCount: 0,
  };
}

function genealogyBatch(
  batchId: string,
  itemName: string,
  origin: 'INTERNALLY_PRODUCED' | 'EXTERNAL_OR_NON_PRODUCED',
) {
  return {
    batchId,
    origin,
    inventoryItemId: `item-${batchId}`,
    itemName,
    itemCategory: 'OTHER' as const,
    unitOfMeasure: 'MILLILITER' as const,
    supplierId: null,
    lotCode: `LOT-${batchId}`,
    receivedAt: '2026-09-30',
    expiresAt: null,
  };
}

function findLink(harness: RouterTestingHarness, label: string): HTMLAnchorElement {
  return Array.from(
    harness.routeNativeElement?.querySelectorAll('a') ?? [],
  ).find((link) => link.textContent?.trim() === label) as HTMLAnchorElement;
}

function findButton(harness: RouterTestingHarness, label: string): HTMLButtonElement {
  return Array.from(
    harness.routeNativeElement?.querySelectorAll('button') ?? [],
  ).find((button) => button.textContent?.includes(label)) as HTMLButtonElement;
}
