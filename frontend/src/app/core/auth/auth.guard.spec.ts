import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, UrlTree } from '@angular/router';
import { firstValueFrom, Observable, of } from 'rxjs';

import { authGuard, loginGuard } from './auth.guard';
import { AuthSessionService, AuthSessionState } from './auth-session.service';

describe('auth guards', () => {
  let state: ReturnType<typeof signal<AuthSessionState>>;
  let bootstrap: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    state = signal<AuthSessionState>({ kind: 'unknown' });
    bootstrap = vi.fn(() => of<AuthSessionState>({ kind: 'unauthenticated' }));
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: AuthSessionService,
          useValue: { state, bootstrap },
        },
      ],
    });
  });

  it('allows backend-confirmed authenticated access', () => {
    state.set({ kind: 'authenticated', username: 'Operator' });

    const result = TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));

    expect(result).toBe(true);
  });

  it('bootstraps unknown state and redirects unauthenticated access to login', async () => {
    const result = TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));

    const resolved = await firstValueFrom(result as Observable<boolean | UrlTree>);

    expect(bootstrap).toHaveBeenCalledTimes(1);
    expect((resolved as UrlTree).toString()).toBe('/login');
  });

  it('redirects an existing session away from login', () => {
    state.set({ kind: 'authenticated', username: 'Operator' });

    const result = TestBed.runInInjectionContext(() => loginGuard({} as never, {} as never));

    expect((result as UrlTree).toString()).toBe('/dashboard');
    expect(TestBed.inject(Router)).toBeTruthy();
  });
});
