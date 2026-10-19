import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import { CustomerApiService } from './customer-api.service';

describe('CustomerApiService', () => {
  let api: CustomerApiService;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api/v1/' },
      ],
    });
    api = TestBed.inject(CustomerApiService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('sends trimmed search, explicit inactive filter and zero-based pagination', () => {
    api.search({ q: ' Ana ', active: false, page: 2, size: 50 }).subscribe();
    const request = http.expectOne('/api/v1/customers?page=2&size=50&q=Ana&active=false');
    expect(request.request.method).toBe('GET');
    request.flush({ content: [] });
  });
  it('omits blank search and absent state filter', () => {
    api.search({ q: ' ', page: 0, size: 20 }).subscribe();
    const request = http.expectOne('/api/v1/customers?page=0&size=20');
    expect(request.request.params.keys()).toEqual(['page', 'size']);
    request.flush({ content: [] });
  });
  it('uses the delivered detail, create, replacement and state-action contracts', () => {
    const body = { name: 'Ana', phone: null, email: null };
    api.getById('id').subscribe();
    let request = http.expectOne('/api/v1/customers/id');
    expect(request.request.method).toBe('GET');
    request.flush({});
    api.register(body).subscribe();
    request = http.expectOne('/api/v1/customers');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(body);
    request.flush({});
    api.update('id', body).subscribe();
    request = http.expectOne('/api/v1/customers/id');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(body);
    request.flush({});
    api.deactivate('id').subscribe();
    request = http.expectOne('/api/v1/customers/id/deactivate');
    expect(request.request.method).toBe('POST');
    request.flush({});
    api.activate('id').subscribe();
    request = http.expectOne('/api/v1/customers/id/activate');
    expect(request.request.method).toBe('POST');
    request.flush({});
  });
});
