import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of } from 'rxjs';

import { API_BASE_URL } from '../../core/config/api-base-url.token';
import { routes } from '../../app.routes';
import { InventoryItemApiService } from '../catalog/data-access/inventory-item-api.service';
import { ProductionFormulaApiService } from './data-access/production-formula-api.service';

describe('production routes', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        { provide: API_BASE_URL, useValue: '/api/v1' },
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
          provide: ProductionFormulaApiService,
          useValue: {
            list: () => of([]),
            getById: () =>
              of({
                id: 'formula-1',
                outputInventoryItemId: 'item',
                outputQuantity: 1,
                outputUnitOfMeasure: 'UNIT',
                ingredients: [],
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

  it('should resolve a stable formula edit route', async () => {
    const harness = await RouterTestingHarness.create('/production/formulas/formula-1');
    expect(harness.routeNativeElement?.textContent).toContain('Editar fórmula de produção');
  });
});
