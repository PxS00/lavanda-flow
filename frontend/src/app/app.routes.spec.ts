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
    const harness = await RouterTestingHarness.create();
    const navigation = harness.navigateByUrl('/');
    await navigationStarted();
    http.expectOne('/api/v1/auth/session').flush({ authenticated: true, username: 'Operator' });
    await navigation;
    http.expectOne('/api/v1/inventory/dashboard').flush(dashboardSummary());

    const router = TestBed.inject(Router);

    expect(router.url).toBe('/dashboard');
  });

  it('should render the dashboard route', async () => {
    const harness = await RouterTestingHarness.create();
    const navigation = harness.navigateByUrl('/dashboard');
    await navigationStarted();
    http.expectOne('/api/v1/auth/session').flush({ authenticated: true, username: 'Operator' });
    await navigation;
    http.expectOne('/api/v1/inventory/dashboard').flush(dashboardSummary());
    harness.fixture.detectChanges();

    expect(harness.routeNativeElement?.textContent).toContain('Painel operacional');
    expect(harness.routeNativeElement?.textContent).toContain('Itens ativos');
  });

  it('should redirect an unauthenticated protected-route reload to login', async () => {
    const harness = await RouterTestingHarness.create();
    const navigation = harness.navigateByUrl('/dashboard');
    await navigationStarted();
    http.expectOne('/api/v1/auth/session').flush({ authenticated: false, username: null });
    await navigation;

    expect(TestBed.inject(Router).url).toBe('/login');
  });

  it.each([
    '/customers',
    '/customers/new',
    '/customers/customer-id',
    '/customers/customer-id/edit',
  ])('protects customer route %s on direct reload', async (url) => {
    const harness = await RouterTestingHarness.create();
    const navigation = harness.navigateByUrl(url);
    await vi.waitFor(() =>
      http.expectOne('/api/v1/auth/session').flush({ authenticated: false, username: null }),
    );
    await navigation;
    expect(TestBed.inject(Router).url).toBe('/login');
  });

  it('loads the authenticated customer list with active defaults', async () => {
    const harness = await RouterTestingHarness.create();
    const navigation = harness.navigateByUrl('/customers');
    await vi.waitFor(() =>
      http.expectOne('/api/v1/auth/session').flush({ authenticated: true, username: 'Operator' }),
    );
    await navigation;
    http
      .expectOne('/api/v1/customers?page=0&size=20&active=true')
      .flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    harness.fixture.detectChanges();
    expect(harness.routeNativeElement?.textContent).toContain('Nenhum cliente encontrado');
  });

  it.each(['/customers/new', '/customers/customer-id', '/customers/customer-id/edit'])(
    'loads authenticated customer workflow %s',
    async (url) => {
      const harness = await RouterTestingHarness.create();
      const navigation = harness.navigateByUrl(url);
      await vi.waitFor(() =>
        http.expectOne('/api/v1/auth/session').flush({ authenticated: true, username: 'Operator' }),
      );
      await navigation;
      if (url !== '/customers/new') {
        http.expectOne('/api/v1/customers/customer-id').flush({
          id: 'customer-id',
          name: 'Ana',
          phone: null,
          email: null,
          active: false,
          createdAt: '2026-10-05T12:00:00Z',
          updatedAt: '2026-10-05T12:00:00Z',
        });
      }
      harness.fixture.detectChanges();
      expect(TestBed.inject(Router).url).toBe(url);
      if (url.endsWith('/edit')) {
        expect(
          harness.routeNativeElement?.querySelector<HTMLInputElement>(
            'input[formControlName="name"]',
          )?.value,
        ).toBe('Ana');
      } else {
        expect(harness.routeNativeElement?.textContent).toContain(
          url === '/customers/new' ? 'Cadastrar cliente' : 'Ana',
        );
      }
    },
  );

  it('should redirect a valid backend session away from login', async () => {
    const harness = await RouterTestingHarness.create();
    const navigation = harness.navigateByUrl('/login');
    await navigationStarted();
    http.expectOne('/api/v1/auth/session').flush({ authenticated: true, username: 'Operator' });
    await navigation;
    http.expectOne('/api/v1/inventory/dashboard').flush(dashboardSummary());

    expect(TestBed.inject(Router).url).toBe('/dashboard');
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

function navigationStarted(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve));
}
