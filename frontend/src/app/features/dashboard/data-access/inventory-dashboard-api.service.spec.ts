import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import { InventoryDashboardApiService } from './inventory-dashboard-api.service';
import { InventoryDashboardDto } from './inventory-dashboard.dto';

describe('InventoryDashboardApiService', () => {
  let service: InventoryDashboardApiService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'https://api.example.test/' },
      ],
    });

    service = TestBed.inject(InventoryDashboardApiService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('should retrieve the exact dashboard summary contract', () => {
    const response: InventoryDashboardDto = {
      asOfDate: '2026-09-01',
      expirationWindowDays: 30,
      activeItemCount: 12,
      lowStockItemCount: 3,
      outOfStockItemCount: 2,
      expiringSoonBatchCount: 4,
      expiredBatchCount: 1,
    };
    let result: InventoryDashboardDto | undefined;
    service.getDashboard().subscribe((data) => (result = data));

    const request = httpTesting.expectOne('https://api.example.test/api/v1/inventory/dashboard');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.keys()).toEqual([]);
    request.flush(response);

    expect(result).toEqual(response);
  });
});
