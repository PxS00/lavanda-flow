import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import { authSessionInterceptor } from './auth-session.interceptor';
import { AuthSessionService } from './auth-session.service';

describe('authSessionInterceptor', () => {
  let http: HttpClient;
  let requests: HttpTestingController;
  let expire: ReturnType<typeof vi.fn>;
  let router: Router;

  beforeEach(() => {
    const state = signal<
      { kind: 'authenticated'; username: string } | { kind: 'unauthenticated' }
    >({ kind: 'authenticated', username: 'Operator' });

    expire = vi.fn(() => state.set({ kind: 'unauthenticated' }));
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authSessionInterceptor])),
        provideHttpClientTesting(),
        {
          provide: AuthSessionService,
          useValue: { setUnauthenticated: expire, state },
        },
      ],
    });
    http = TestBed.inject(HttpClient);
    requests = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
  });

  afterEach(() => requests.verify());

  it('expires operational state and propagates an operational 401 without retrying', () => {
    let received: unknown;
    http.get('/api/v1/inventory/dashboard').subscribe({ error: (error) => (received = error) });

    const request = requests.expectOne('/api/v1/inventory/dashboard');
    request.flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(expire).toHaveBeenCalledTimes(1);
    expect(router.navigateByUrl).toHaveBeenCalledWith('/login');
    expect(received).toBeTruthy();
    requests.expectNone('/api/v1/inventory/dashboard');
  });

  it('handles concurrent operational 401 responses idempotently', () => {
    http.get('/api/v1/inventory/dashboard').subscribe({ error: () => undefined });
    http.get('/api/v1/inventory/alerts').subscribe({ error: () => undefined });

    const dashboardRequest = requests.expectOne('/api/v1/inventory/dashboard');
    const alertsRequest = requests.expectOne('/api/v1/inventory/alerts');

    dashboardRequest.flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(expire).toHaveBeenCalledTimes(1);
    expect(router.navigateByUrl).toHaveBeenCalledTimes(1);

    alertsRequest.flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(expire).toHaveBeenCalledTimes(1);
    expect(router.navigateByUrl).toHaveBeenCalledTimes(1);
  });

  it('leaves an invalid login 401 for the login form without redirecting', () => {
    let received: unknown;
    http.post('/api/v1/auth/login', { username: 'operator', password: 'secret' }).subscribe({
      error: (error) => (received = error),
    });

    requests.expectOne('/api/v1/auth/login').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(expire).not.toHaveBeenCalled();
    expect(router.navigateByUrl).not.toHaveBeenCalled();
    expect(received).toBeTruthy();
  });
});
