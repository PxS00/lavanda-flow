import { ApplicationConfig, LOCALE_ID, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { MatPaginatorIntl } from '@angular/material/paginator';
import { environment } from '../environments/environment';
import { API_BASE_URL } from './core/config/api-base-url.token';
import { routes } from './app.routes';
import { APPLICATION_LOCALE } from './core/i18n/pt-br-locale';
import { createPtBrPaginatorIntl } from './core/i18n/pt-br-paginator-intl';

export const appConfig: ApplicationConfig = {
  providers: [ provideBrowserGlobalErrorListeners(), provideRouter(routes), provideHttpClient(),
    { provide: LOCALE_ID, useValue: APPLICATION_LOCALE },
    { provide: MatPaginatorIntl, useFactory: createPtBrPaginatorIntl },
    {
      provide: API_BASE_URL,
      useValue: environment.apiBaseUrl,
    },
  ],
};
