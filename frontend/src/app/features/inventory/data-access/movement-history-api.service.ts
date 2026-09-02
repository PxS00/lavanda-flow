import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import {
  InventoryItemMovementHistoryQuery,
  MovementHistoryPageDto,
} from './inventory-operations.dto';

/** Typed HTTP client for immutable inventory movement history. */
@Service()
export class MovementHistoryApiService {
  private readonly http = inject(HttpClient);
  private readonly movementsUrl = buildMovementsUrl(inject(API_BASE_URL));

  search(query: InventoryItemMovementHistoryQuery): Observable<MovementHistoryPageDto> {
    const params = new HttpParams()
      .set('inventoryItemId', query.inventoryItemId)
      .set('page', query.page)
      .set('size', query.size);

    return this.http.get<MovementHistoryPageDto>(this.movementsUrl, { params });
  }
}

function buildMovementsUrl(apiBaseUrl: string): string {
  const normalizedBaseUrl = apiBaseUrl.replace(/\/+$/, '');
  const versionedBaseUrl = normalizedBaseUrl.endsWith('/api/v1')
    ? normalizedBaseUrl
    : `${normalizedBaseUrl}/api/v1`;

  return `${versionedBaseUrl}/inventory/movements`;
}
