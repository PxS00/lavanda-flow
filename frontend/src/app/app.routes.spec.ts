import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { routes } from './app.routes';
import { API_BASE_URL } from './core/config/api-base-url.token';

describe('application routes', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api/v1' },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('should redirect the root route to dashboard', async () => {
    await RouterTestingHarness.create('/');
    http.expectOne('/api/v1/inventory/dashboard').flush(dashboardSummary());

    const router = TestBed.inject(Router);

    expect(router.url).toBe('/dashboard');
  });

  it('should render the dashboard route', async () => {
    const harness = await RouterTestingHarness.create('/dashboard');
    http.expectOne('/api/v1/inventory/dashboard').flush(dashboardSummary());
    harness.fixture.detectChanges();

    expect(harness.routeNativeElement?.textContent).toContain('Painel operacional');
    expect(harness.routeNativeElement?.textContent).toContain('Itens ativos');
  });
});

function dashboardSummary() {
  return {
    asOfDate: '2026-09-01',
    expirationWindowDays: 30,
    activeItemCount: 12,
    lowStockItemCount: 3,
    outOfStockItemCount: 2,
    expiringSoonBatchCount: 4,
    expiredBatchCount: 1,
  };
}
