import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import { RegisterFefoWithdrawalDto, RegisterFefoWithdrawalRequest } from './fefo-withdrawal.dto';

/** Typed HTTP client for backend-authoritative automatic FEFO withdrawals. */
@Service()
export class FefoWithdrawalApiService {
  private readonly http = inject(HttpClient);
  private readonly inventoryItemsUrl = buildInventoryItemsUrl(inject(API_BASE_URL));

  register(
    inventoryItemId: string,
    request: RegisterFefoWithdrawalRequest,
  ): Observable<RegisterFefoWithdrawalDto> {
    return this.http.post<RegisterFefoWithdrawalDto>(
      `${this.inventoryItemsUrl}/${inventoryItemId}/withdrawals`,
      request,
    );
  }
}

function buildInventoryItemsUrl(apiBaseUrl: string): string {
  const normalizedBaseUrl = apiBaseUrl.replace(/\/+$/, '');
  const versionedBaseUrl = normalizedBaseUrl.endsWith('/api/v1')
    ? normalizedBaseUrl
    : `${normalizedBaseUrl}/api/v1`;

  return `${versionedBaseUrl}/inventory/items`;
}
