import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of } from 'rxjs';

import { API_BASE_URL } from '../../core/config/api-base-url.token';
import { routes } from '../../app.routes';
import { InventoryItemApiService } from './data-access/inventory-item-api.service';

describe('catalog routes', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        { provide: API_BASE_URL, useValue: '/api/v1' },
        {
          provide: InventoryItemApiService,
          useValue: {
            search: () => of({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
            getById: (id: string) =>
              of({
                id,
                name: 'Direct item',
                description: null,
                category: 'OTHER',
                unitOfMeasure: 'UNIT',
                active: true,
              }),
          },
        },
      ],
    });
  });

  it('should lazy load the catalog feature from the application routes', () => {
    const shellRoute = routes.find((route) => route.path === '');
    const catalogRoute = shellRoute?.children?.find((route) => route.path === 'catalog');

    expect(catalogRoute?.loadChildren).toBeTypeOf('function');
  });

  it('should resolve /catalog/new as registration rather than an item ID', async () => {
    const harness = await RouterTestingHarness.create('/catalog/new');

    expect(harness.routeNativeElement?.textContent).toContain('Register inventory item');
  });

  it('should resolve a stable detail route using its route ID', async () => {
    const harness = await RouterTestingHarness.create('/catalog/item-123');

    expect(harness.routeNativeElement?.textContent).toContain('Direct item');
  });
});
