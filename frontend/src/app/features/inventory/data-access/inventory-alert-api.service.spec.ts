import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import { ExpirationAlertsDto, LowStockAlertsDto } from './inventory-alert.dto';
import { InventoryAlertApiService } from './inventory-alert-api.service';

describe('InventoryAlertApiService', () => {
  let service: InventoryAlertApiService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'https://api.example.test/' },
      ],
    });

    service = TestBed.inject(InventoryAlertApiService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('should retrieve low-stock alerts without client-side query parameters', () => {
    const response: LowStockAlertsDto = { asOfDate: '2026-09-01', alerts: [] };
    service.getLowStockAlerts().subscribe();

    const request = httpTesting.expectOne('https://api.example.test/api/v1/inventory/alerts/low-stock');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.keys()).toEqual([]);
    request.flush(response);
  });

  it('should allow the backend to resolve the initial expiration window', () => {
    const response: ExpirationAlertsDto = { asOfDate: '2026-09-01', windowDays: 30, alerts: [] };
    service.getExpirationAlerts().subscribe();

    const request = httpTesting.expectOne('https://api.example.test/api/v1/inventory/alerts/expiration');
    expect(request.request.params.keys()).toEqual([]);
    request.flush(response);
  });

  it('should request an explicit zero-day expiration window', () => {
    const response: ExpirationAlertsDto = { asOfDate: '2026-09-01', windowDays: 0, alerts: [] };
    service.getExpirationAlerts(0).subscribe();

    const request = httpTesting.expectOne(
      'https://api.example.test/api/v1/inventory/alerts/expiration?windowDays=0',
    );
    expect(request.request.method).toBe('GET');
    request.flush(response);
  });
});
