import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import { InventoryStockListApiService } from './inventory-stock-list-api.service';

describe('InventoryStockListApiService', () => {
  let service: InventoryStockListApiService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'https://api.example.test/' },
      ],
    });
    service = TestBed.inject(InventoryStockListApiService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('should encode repeated categories with pagination', () => {
    service.search({ categories: ['BASE', 'ALCOHOL'], page: 1, size: 50 }).subscribe();

    const request = httpTesting.expectOne(
      'https://api.example.test/api/v1/inventory/items?page=1&size=50&category=BASE&category=ALCOHOL',
    );
    expect(request.request.method).toBe('GET');
    request.flush({
      content: [], page: 1, size: 50, totalElements: 0, totalPages: 0,
      asOfDate: '2026-09-18', expirationWindowDays: 30,
    });
  });

  it('should omit the category parameter for all stock', () => {
    service.search({ categories: [], page: 0, size: 20 }).subscribe();

    const request = httpTesting.expectOne(
      'https://api.example.test/api/v1/inventory/items?page=0&size=20',
    );
    expect(request.request.params.has('category')).toBe(false);
    request.flush({
      content: [], page: 0, size: 20, totalElements: 0, totalPages: 0,
      asOfDate: '2026-09-18', expirationWindowDays: 30,
    });
  });
});
