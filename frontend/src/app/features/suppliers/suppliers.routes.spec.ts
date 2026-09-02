import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of } from 'rxjs';

import { API_BASE_URL } from '../../core/config/api-base-url.token';
import { routes } from '../../app.routes';
import { SupplierApiService } from './data-access/supplier-api.service';

describe('supplier routes', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        { provide: API_BASE_URL, useValue: '/api/v1' },
        {
          provide: SupplierApiService,
          useValue: {
            search: () => of({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
            getById: (id: string) =>
              of({
                id,
                name: 'Direct supplier',
                identifier: null,
                contact: null,
                notes: null,
                active: true,
              }),
          },
        },
      ],
    });
  });

  it('should lazy load the supplier feature from the application routes', () => {
    const shellRoute = routes.find((route) => route.path === '');
    const supplierRoute = shellRoute?.children?.find((route) => route.path === 'suppliers');

    expect(supplierRoute?.loadChildren).toBeTypeOf('function');
  });

  it('should resolve /suppliers/new as registration rather than a supplier ID', async () => {
    const harness = await RouterTestingHarness.create('/suppliers/new');

    expect(harness.routeNativeElement?.textContent).toContain('Cadastrar fornecedor');
  });

  it('should resolve a stable detail route using its route ID', async () => {
    const harness = await RouterTestingHarness.create('/suppliers/supplier-123');

    expect(harness.routeNativeElement?.textContent).toContain('Direct supplier');
  });
});
