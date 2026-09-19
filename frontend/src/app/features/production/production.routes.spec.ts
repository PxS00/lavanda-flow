import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of } from 'rxjs';

import { API_BASE_URL } from '../../core/config/api-base-url.token';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { authenticatedOperatorSession } from '../../core/auth/testing/authenticated-operator-session';
import { routes } from '../../app.routes';
import { InventoryItemApiService } from '../catalog/data-access/inventory-item-api.service';
import { ProductionExecutionApiService } from './data-access/production-execution-api.service';
import { ProductionFormulaApiService } from './data-access/production-formula-api.service';
import { ProductionGenealogyApiService } from './data-access/production-genealogy-api.service';

describe('production routes', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        { provide: API_BASE_URL, useValue: '/api/v1' },
        { provide: AuthSessionService, useValue: authenticatedOperatorSession() },
        {
          provide: InventoryItemApiService,
          useValue: {
            search: () => of({
              content: [],
              page: 0,
              size: 100,
              totalElements: 0,
              totalPages: 0,
            }),
          },
        },
        {
          provide: ProductionExecutionApiService,
          useValue: {
            search: () =>
              of({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
            getById: (executionId: string) =>
              of({
                executionId,
                formulaId: 'formula-1',
                outputInventoryItemId: 'item-1',
                outputItemName: 'Sabonete',
                outputUnitOfMeasure: 'MILLILITER',
                outputBatchId: 'batch-1',
                outputQuantity: '10',
                lotCode: 'LOT-1',
                lotCodeMode: 'MANUAL',
                productionDate: '2026-09-18',
                outputReceivedAt: '2026-09-18',
                outputExpiresAt: null,
                completedAt: '2026-09-18T12:00:00Z',
                consumptions: [],
              }),
          },
        },
        {
          provide: ProductionFormulaApiService,
          useValue: {
            list: () => of([]),
            getById: () =>
              of({
                id: 'formula-1',
                outputInventoryItemId: 'item',
                outputQuantity: '1',
                outputUnitOfMeasure: 'UNIT',
                ingredients: [],
              }),
          },
        },
        {
          provide: ProductionGenealogyApiService,
          useValue: {
            getBatchGenealogy: () =>
              of({
                direction: 'BOTH',
                rootBatch: {
                  batchId: 'batch-1',
                  origin: 'EXTERNAL_OR_NON_PRODUCED',
                  inventoryItemId: 'item-1',
                  itemName: 'Essência de lavanda',
                  itemCategory: 'ESSENCE',
                  unitOfMeasure: 'MILLILITER',
                  supplierId: 'supplier-1',
                  lotCode: 'LOT-1',
                  receivedAt: '2026-09-01',
                  expiresAt: null,
                },
                upstream: [],
                downstream: [],
              }),
          },
        },
      ],
    });
  });

  it('should lazy load production from the application routes', () => {
    const shellRoute = routes.find((route) => route.path === '');
    expect(shellRoute?.children?.find((route) => route.path === 'production')?.loadChildren).toBeTypeOf(
      'function',
    );
  });

  it('should resolve formula listing and new-form routes before the formula ID route', async () => {
    const harness = await RouterTestingHarness.create('/production/formulas');
    expect(harness.routeNativeElement?.textContent).toContain('Fórmulas de produção');

    await harness.navigateByUrl('/production/formulas/new');
    expect(harness.routeNativeElement?.textContent).toContain('Cadastrar fórmula de produção');
  });

  it('should resolve the production registration route', async () => {
    const harness = await RouterTestingHarness.create('/production/executions/new');
    expect(harness.routeNativeElement?.textContent).toContain('Registrar produção interna');
  });

  it('should keep new literal before history and direct execution detail routes', async () => {
    const harness = await RouterTestingHarness.create('/production/executions');
    expect(harness.routeNativeElement?.textContent).toContain('Histórico de produção');

    await harness.navigateByUrl('/production/executions/execution-1');
    expect(harness.routeNativeElement?.textContent).toContain('Detalhes da produção');
    expect(harness.routeNativeElement?.textContent).toContain('Sabonete');

    await harness.navigateByUrl('/production/executions/new');
    expect(harness.routeNativeElement?.textContent).toContain('Registrar produção interna');
    expect(harness.routeNativeElement?.textContent).not.toContain('Detalhes da produção');
  });

  it('should resolve genealogy by stable batch identity', async () => {
    const harness = await RouterTestingHarness.create('/production/genealogy/batches/batch-1');
    expect(harness.routeNativeElement?.textContent).toContain('Genealogia do lote');
    expect(harness.routeNativeElement?.textContent).toContain('Essência de lavanda');
  });

  it('should resolve a stable formula edit route', async () => {
    const harness = await RouterTestingHarness.create('/production/formulas/formula-1');
    expect(harness.routeNativeElement?.textContent).toContain('Editar fórmula de produção');
  });
});
