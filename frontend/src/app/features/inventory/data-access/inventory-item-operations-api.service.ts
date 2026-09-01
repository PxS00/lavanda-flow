import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import {
  BatchInventoryDto,
  ConfigureMinimumStockLevelRequest,
  InventoryItemOverviewDto,
  MinimumStockLevelDto,
} from './inventory-operations.dto';

/** Typed HTTP client for item-level operational inventory endpoints. */
@Service()
export class InventoryItemOperationsApiService {
  private readonly http = inject(HttpClient);
  private readonly inventoryItemsUrl = buildInventoryItemsUrl(inject(API_BASE_URL));

  getOverview(inventoryItemId: string): Observable<InventoryItemOverviewDto> {
    return this.http.get<InventoryItemOverviewDto>(
      `${this.inventoryItemsUrl}/${inventoryItemId}/overview`,
    );
  }

  getBatches(inventoryItemId: string): Observable<BatchInventoryDto> {
    return this.http.get<BatchInventoryDto>(`${this.inventoryItemsUrl}/${inventoryItemId}/batches`);
  }

  getMinimumStockLevel(inventoryItemId: string): Observable<MinimumStockLevelDto> {
    return this.http.get<MinimumStockLevelDto>(
      `${this.inventoryItemsUrl}/${inventoryItemId}/minimum-stock-level`,
    );
  }

  configureMinimumStockLevel(
    inventoryItemId: string,
    request: ConfigureMinimumStockLevelRequest,
  ): Observable<MinimumStockLevelDto> {
    return this.http.put<MinimumStockLevelDto>(
      `${this.inventoryItemsUrl}/${inventoryItemId}/minimum-stock-level`,
      request,
    );
  }

  deleteMinimumStockLevel(inventoryItemId: string): Observable<void> {
    return this.http.delete<void>(`${this.inventoryItemsUrl}/${inventoryItemId}/minimum-stock-level`);
  }
}

function buildInventoryItemsUrl(apiBaseUrl: string): string {
  const normalizedBaseUrl = apiBaseUrl.replace(/\/+$/, '');
  const versionedBaseUrl = normalizedBaseUrl.endsWith('/api/v1')
    ? normalizedBaseUrl
    : `${normalizedBaseUrl}/api/v1`;

  return `${versionedBaseUrl}/inventory/items`;
}
