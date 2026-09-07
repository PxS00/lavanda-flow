import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import { InventoryDashboardDto } from './inventory-dashboard.dto';

/** Typed HTTP client for the backend-authoritative operational dashboard. */
@Service()
export class InventoryDashboardApiService {
  private readonly http = inject(HttpClient);
  private readonly dashboardUrl = buildDashboardUrl(inject(API_BASE_URL));

  getDashboard(): Observable<InventoryDashboardDto> {
    return this.http.get<InventoryDashboardDto>(this.dashboardUrl);
  }
}

function buildDashboardUrl(apiBaseUrl: string): string {
  const normalizedBaseUrl = apiBaseUrl.replace(/\/+$/, '');
  const versionedBaseUrl = normalizedBaseUrl.endsWith('/api/v1')
    ? normalizedBaseUrl
    : `${normalizedBaseUrl}/api/v1`;

  return `${versionedBaseUrl}/inventory/dashboard`;
}
