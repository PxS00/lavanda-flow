import { provideHttpClient, withInterceptors, withXsrfConfiguration } from '@angular/common/http';
import { MatPaginatorIntl } from '@angular/material/paginator';
import { ApplicationConfig, LOCALE_ID, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';

import { environment } from '../environments/environment';
import { authSessionInterceptor } from './core/auth/auth-session.interceptor';
import { API_BASE_URL } from './core/config/api-base-url.token';
import { APPLICATION_LOCALE } from './core/i18n/pt-br-locale';
import { createPtBrPaginatorIntl } from './core/i18n/pt-br-paginator-intl';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(
      withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }),
      withInterceptors([authSessionInterceptor]),
    ),
    { provide: LOCALE_ID, useValue: APPLICATION_LOCALE },
    { provide: MatPaginatorIntl, useFactory: createPtBrPaginatorIntl },
    {
      provide: API_BASE_URL,
      useValue: environment.apiBaseUrl,
    },
  ],
};
