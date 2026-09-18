import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of } from 'rxjs';

import { routes } from '../../app.routes';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { authenticatedOperatorSession } from '../../core/auth/testing/authenticated-operator-session';
import { InventoryItemApiService } from '../catalog/data-access/inventory-item-api.service';
import { InventoryItemOperationsApiService } from './data-access/inventory-item-operations-api.service';
import { FefoWithdrawalApiService } from './data-access/fefo-withdrawal-api.service';
import { InventoryAlertApiService } from './data-access/inventory-alert-api.service';
import { InventoryStockListApiService } from './data-access/inventory-stock-list-api.service';
import { MovementHistoryApiService } from './data-access/movement-history-api.service';

describe('inventory routes', () => {
  const inventoryItemId = 'bd194732-51cf-4f73-bc5d-3a9f9337adcc';
  let stockSearch: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    stockSearch = vi.fn(() => of({
      content: [], page: 0, size: 20, totalElements: 0, totalPages: 0,
      asOfDate: '2026-09-18', expirationWindowDays: 30,
    }));
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        { provide: AuthSessionService, useValue: authenticatedOperatorSession() },
        {
          provide: InventoryItemApiService,
          useValue: {
            getById: () =>
              of({
                id: inventoryItemId,
                name: 'Lavender Essence',
                description: null,
                category: 'ESSENCE',
                unitOfMeasure: 'MILLILITER',
                active: true,
                essenceReference: '027',
                productionTypeCode: null,
                gender: null,
              }),
          },
        },
        {
          provide: InventoryItemOperationsApiService,
          useValue: {
            getOverview: () =>
              of({
                inventoryItemId,
                name: 'Lavender Essence',
                category: 'ESSENCE',
                unitOfMeasure: 'MILLILITER',
                active: true,
                asOfDate: '2026-09-01',
                expirationWindowDays: 30,
                totalCurrentQuantity: '20',
                availableQuantity: '18',
                minimumQuantity: null,
                lowStock: false,
                outOfStock: false,
                nonZeroBatchCount: 1,
                nearestExpiration: null,
                expiredBatchCount: 0,
                expiringSoonBatchCount: 0,
              }),
            getBatches: () => of({ inventoryItemId, asOfDate: '2026-09-01', batches: [] }),
            getMinimumStockLevel: () => of({ inventoryItemId, minimumQuantity: '5' }),
          },
        },
        {
          provide: MovementHistoryApiService,
          useValue: {
            search: () =>
              of({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
          },
        },
        { provide: FefoWithdrawalApiService, useValue: { register: vi.fn() } },
        {
          provide: InventoryAlertApiService,
          useValue: {
            getLowStockAlerts: () => of({ asOfDate: '2026-09-01', alerts: [] }),
            getExpirationAlerts: () => of({ asOfDate: '2026-09-01', windowDays: 30, alerts: [] }),
          },
        },
        {
          provide: InventoryStockListApiService,
          useValue: { search: stockSearch },
        },
      ],
    });
  });

  it('should lazy load the inventory feature from the application routes', () => {
    const shellRoute = routes.find((route) => route.path === '');
    const inventoryRoute = shellRoute?.children?.find((route) => route.path === 'inventory');

    expect(inventoryRoute?.loadChildren).toBeTypeOf('function');
  });

  it('should resolve a stable operational item route', async () => {
    const harness = await RouterTestingHarness.create(`/inventory/items/${inventoryItemId}`);

    expect(harness.routeNativeElement?.textContent).toContain('Operações de estoque');
    expect(harness.routeNativeElement?.textContent).toContain('Lavender Essence');
  });

  it('should resolve the operational alerts route', async () => {
    const harness = await RouterTestingHarness.create('/inventory/alerts');

    expect(harness.routeNativeElement?.textContent).toContain('Alertas operacionais');
  });

  it('should redirect inventory root to all stock', async () => {
    const harness = await RouterTestingHarness.create('/inventory');

    expect(harness.routeNativeElement?.textContent).toContain('Estoque');
    expect(TestBed.inject(Router).url).toBe('/inventory/all');
  });

  it.each([
    ['/inventory/all', 'Estoque', []],
    ['/inventory/finished-products', 'Produtos finalizados', ['FINISHED_PRODUCT']],
    ['/inventory/essences', 'Essências', ['ESSENCE']],
    ['/inventory/inputs', 'Insumos', ['BASE', 'ALCOHOL', 'CHEMICAL_INPUT', 'COLORANT', 'FIXATIVE']],
    ['/inventory/packaging', 'Embalagens e componentes', ['BOTTLE', 'VALVE', 'CAP', 'LABEL', 'PACKAGING']],
  ])('should resolve %s and request its approved categories', async (path, title, categories) => {
    const harness = await RouterTestingHarness.create(path as string);

    expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toContain(title);
    expect(stockSearch).toHaveBeenCalledWith({ categories, page: 0, size: 20 });
    const currentLink = harness.routeNativeElement?.querySelector(
      `.group-navigation a[href="${path}"]`,
    );
    expect(currentLink?.getAttribute('aria-current')).toBe('page');
  });
});
