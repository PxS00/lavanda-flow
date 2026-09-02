import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import { ExpirationAlertsDto, LowStockAlertsDto } from './inventory-alert.dto';

/** Typed HTTP client for backend-authoritative inventory operational alerts. */
@Service()
export class InventoryAlertApiService {
  private readonly http = inject(HttpClient);
  private readonly alertsUrl = buildAlertsUrl(inject(API_BASE_URL));

  getLowStockAlerts(): Observable<LowStockAlertsDto> {
    return this.http.get<LowStockAlertsDto>(`${this.alertsUrl}/low-stock`);
  }

  getExpirationAlerts(windowDays?: number): Observable<ExpirationAlertsDto> {
    const options = windowDays === undefined ? {} : { params: new HttpParams().set('windowDays', windowDays) };
    return this.http.get<ExpirationAlertsDto>(`${this.alertsUrl}/expiration`, options);
  }
}

function buildAlertsUrl(apiBaseUrl: string): string {
  const normalizedBaseUrl = apiBaseUrl.replace(/\/+$/, '');
  const versionedBaseUrl = normalizedBaseUrl.endsWith('/api/v1')
    ? normalizedBaseUrl
    : `${normalizedBaseUrl}/api/v1`;

  return `${versionedBaseUrl}/inventory/alerts`;
}
