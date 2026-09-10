import { HttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { appConfig } from './app.config';

describe('appConfig', () => {
  it('configures Angular XSRF with the backend cookie and header names', () => {
    TestBed.configureTestingModule({ providers: [...appConfig.providers, provideHttpClientTesting()] });
    document.cookie = 'XSRF-TOKEN=csrf-test-value; path=/';
    const http = TestBed.inject(HttpClient);
    const requests = TestBed.inject(HttpTestingController);

    http.post('/api/v1/auth/login', {}).subscribe();

    const request = requests.expectOne('/api/v1/auth/login');
    expect(request.request.headers.get('X-XSRF-TOKEN')).toBe('csrf-test-value');
    request.flush({});
    requests.verify();
    document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/';
  });
});
