import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of } from 'rxjs';

import { routes } from '../../app.routes';
import { InventoryItemOperationsApiService } from './data-access/inventory-item-operations-api.service';
import { FefoWithdrawalApiService } from './data-access/fefo-withdrawal-api.service';
import { InventoryAlertApiService } from './data-access/inventory-alert-api.service';
import { MovementHistoryApiService } from './data-access/movement-history-api.service';

describe('inventory routes', () => {
  const inventoryItemId = 'bd194732-51cf-4f73-bc5d-3a9f9337adcc';

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
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
                totalCurrentQuantity: 20,
                availableQuantity: 18,
                minimumQuantity: null,
                lowStock: false,
                outOfStock: false,
                nonZeroBatchCount: 1,
                nearestExpiration: null,
                expiredBatchCount: 0,
                expiringSoonBatchCount: 0,
              }),
            getBatches: () => of({ inventoryItemId, asOfDate: '2026-09-01', batches: [] }),
            getMinimumStockLevel: () => of({ inventoryItemId, minimumQuantity: 5 }),
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
});
