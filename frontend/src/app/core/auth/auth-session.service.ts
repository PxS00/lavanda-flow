import { HttpErrorResponse } from '@angular/common/http';
import { computed, inject, Service, signal } from '@angular/core';
import { catchError, defer, finalize, map, Observable, of, shareReplay, switchMap, throwError } from 'rxjs';

import { mapHttpError } from '../http/map-http-error';
import { UiError } from '../http/ui-error';
import { AuthApiService } from './auth-api.service';
import { AuthSessionDto, LoginRequest } from './auth.dto';

export type AuthSessionState =
  | { readonly kind: 'unknown' }
  | { readonly kind: 'loading' }
  | { readonly kind: 'unauthenticated' }
  | { readonly kind: 'authenticated'; readonly username: string }
  | { readonly kind: 'error'; readonly error: UiError };

/** In-memory browser session state backed by the authoritative backend session endpoint. */
@Service()
export class AuthSessionService {
  private readonly authApi = inject(AuthApiService);
  private readonly sessionState = signal<AuthSessionState>({ kind: 'unknown' });
  private bootstrapRequest: Observable<AuthSessionState> | undefined;

  readonly state = this.sessionState.asReadonly();
  readonly isAuthenticated = computed(() => this.sessionState().kind === 'authenticated');
  readonly username = computed(() => {
    const state = this.sessionState();
    return state.kind === 'authenticated' ? state.username : null;
  });

  bootstrap(): Observable<AuthSessionState> {
    if (this.bootstrapRequest !== undefined) {
      return this.bootstrapRequest;
    }

    this.sessionState.set({ kind: 'loading' });
    this.bootstrapRequest = defer(() => this.authApi.getSession()).pipe(
      map((session) => this.applySession(session)),
      catchError((error: unknown) => {
        const state: AuthSessionState = { kind: 'error', error: mapHttpError(error) };
        this.sessionState.set(state);
        return of(state);
      }),
      finalize(() => (this.bootstrapRequest = undefined)),
      shareReplay({ bufferSize: 1, refCount: false }),
    );

    return this.bootstrapRequest;
  }

  refresh(): Observable<AuthSessionState> {
    return this.bootstrap();
  }

  login(request: LoginRequest): Observable<AuthSessionState> {
    return this.authApi.login(request).pipe(switchMap(() => this.refresh()));
  }

  logout(): Observable<AuthSessionState> {
    return this.authApi.logout().pipe(
      switchMap(() => {
        this.setUnauthenticated();
        return this.refresh();
      }),
      catchError((error: unknown) => {
        if (error instanceof HttpErrorResponse && error.status === 401) {
          this.setUnauthenticated();
          return this.refresh();
        }

        return throwError(() => error);
      }),
    );
  }

  setUnauthenticated(): void {
    this.sessionState.set({ kind: 'unauthenticated' });
  }

  private applySession(session: AuthSessionDto): AuthSessionState {
    const state: AuthSessionState = session.authenticated && session.username !== null
      ? { kind: 'authenticated', username: session.username }
      : { kind: 'unauthenticated' };
    this.sessionState.set(state);
    return state;
  }
}
