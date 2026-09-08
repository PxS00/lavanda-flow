import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { tap } from 'rxjs';

import { AuthSessionService } from './auth-session.service';

export const authSessionInterceptor: HttpInterceptorFn = (request, next) => {
  const authSession = inject(AuthSessionService);
  const router = inject(Router);

  return next(request).pipe(
    tap({
      error: (error: unknown) => {
        if (
          error instanceof HttpErrorResponse &&
          error.status === 401 &&
          isOperationalRequest(request.url) &&
          authSession.state().kind !== 'unauthenticated'
        ) {
          authSession.setUnauthenticated();

          if (router.url !== '/login') {
            void router.navigateByUrl('/login');
          }
        }
      },
    }),
  );
};

function isOperationalRequest(url: string): boolean {
  const path = url.split('?')[0];
  return path.includes('/api/v1/') && !path.includes('/api/v1/auth/');
}
