import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map, Observable } from 'rxjs';

import { AuthSessionService, AuthSessionState } from './auth-session.service';

export const authGuard: CanActivateFn = () => {
  const authSession = inject(AuthSessionService);
  const router = inject(Router);
  const state = authSession.state();

  if (state.kind === 'authenticated') {
    return true;
  }

  if (state.kind !== 'unknown' && state.kind !== 'loading') {
    return router.createUrlTree(['/login']);
  }

  return resolveAccess(authSession.bootstrap(), router);
};

export const loginGuard: CanActivateFn = () => {
  const authSession = inject(AuthSessionService);
  const router = inject(Router);
  const state = authSession.state();

  if (state.kind === 'authenticated') {
    return router.createUrlTree(['/dashboard']);
  }

  if (state.kind !== 'unknown' && state.kind !== 'loading') {
    return true;
  }

  return authSession.bootstrap().pipe(
    map((session) => (session.kind === 'authenticated' ? router.createUrlTree(['/dashboard']) : true)),
  );
};

function resolveAccess(
  state: Observable<AuthSessionState>,
  router: Router,
): Observable<boolean | ReturnType<Router['createUrlTree']>> {
  return state.pipe(
    map((session) => (session.kind === 'authenticated' ? true : router.createUrlTree(['/login']))),
  );
}
