import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';

import { AuthApiService } from './auth-api.service';
import { AuthSessionService } from './auth-session.service';

describe('AuthSessionService', () => {
  let service: AuthSessionService;
  let api: {
    getSession: ReturnType<typeof vi.fn>;
    login: ReturnType<typeof vi.fn>;
    logout: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    api = {
      getSession: vi.fn(),
      login: vi.fn(),
      logout: vi.fn(),
    };
    TestBed.configureTestingModule({ providers: [{ provide: AuthApiService, useValue: api }] });
    service = TestBed.inject(AuthSessionService);
  });

  it('bootstraps unauthenticated state from the backend', () => {
    api.getSession.mockReturnValue(of({ authenticated: false, username: null }));

    service.bootstrap().subscribe();

    expect(service.state()).toEqual({ kind: 'unauthenticated' });
  });

  it('bootstraps canonical authenticated state from the backend', () => {
    api.getSession.mockReturnValue(of({ authenticated: true, username: 'Operator' }));

    service.bootstrap().subscribe();

    expect(service.state()).toEqual({ kind: 'authenticated', username: 'Operator' });
    expect(service.username()).toBe('Operator');
  });

  it('shares an in-flight session refresh instead of starting concurrent bootstrap requests', () => {
    const session = new Subject<{ authenticated: boolean; username: string | null }>();
    api.getSession.mockReturnValue(session);

    service.refresh().subscribe();
    service.refresh().subscribe();

    expect(api.getSession).toHaveBeenCalledTimes(1);

    session.next({ authenticated: false, username: null });
    session.complete();

    expect(service.state()).toEqual({ kind: 'unauthenticated' });
  });

  it('refreshes the authoritative session after login before becoming ready', () => {
    api.login.mockReturnValue(of({ authenticated: true, username: 'Operator' }));
    api.getSession.mockReturnValue(of({ authenticated: true, username: 'Operator' }));

    service.login({ username: 'operator', password: 'secret' }).subscribe();

    expect(api.login).toHaveBeenCalledWith({ username: 'operator', password: 'secret' });
    expect(api.getSession).toHaveBeenCalledTimes(1);
    expect(service.state()).toEqual({ kind: 'authenticated', username: 'Operator' });
  });

  it('keeps an invalid login error available to the form without storing credentials', () => {
    const error = new HttpErrorResponse({
      status: 401,
      error: {
        timestamp: '2026-09-07T00:00:00Z',
        status: 401,
        error: 'Unauthorized',
        code: 'AUTHENTICATION_FAILED',
        message: 'Authentication failed',
        path: '/api/v1/auth/login',
        details: null,
      },
    });
    api.login.mockReturnValue(throwError(() => error));
    let received: unknown;

    service.login({ username: 'operator', password: 'secret' }).subscribe({ error: (value) => (received = value) });

    expect(received).toBe(error);
    expect(service.state()).toEqual({ kind: 'unknown' });
    expect(Object.values(service.state())).not.toContain('secret');
  });

  it('clears state and refreshes unauthenticated XSRF/session state after logout', () => {
    const refresh = new Subject<{ authenticated: boolean; username: string | null }>();
    api.getSession.mockReturnValueOnce(of({ authenticated: true, username: 'Operator' }));
    service.bootstrap().subscribe();
    expect(service.state()).toEqual({ kind: 'authenticated', username: 'Operator' });

    api.logout.mockReturnValue(of(void 0));
    api.getSession.mockReturnValue(refresh);

    service.logout().subscribe();

    expect(service.state()).toEqual({ kind: 'loading' });
    expect(api.getSession).toHaveBeenCalledTimes(2);
    refresh.next({ authenticated: false, username: null });
    expect(service.state()).toEqual({ kind: 'unauthenticated' });
  });

  it('treats an expired logout session as unauthenticated and refreshes CSRF state', () => {
    api.logout.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 401 })));
    api.getSession.mockReturnValue(of({ authenticated: false, username: null }));

    service.logout().subscribe();

    expect(api.getSession).toHaveBeenCalledTimes(1);
    expect(service.state()).toEqual({ kind: 'unauthenticated' });
  });

  it('does not leave stale authenticated state after a bootstrap failure', () => {
    api.getSession.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));
    service.setUnauthenticated();

    service.refresh().subscribe();

    expect(service.state().kind).toBe('error');
  });
});
