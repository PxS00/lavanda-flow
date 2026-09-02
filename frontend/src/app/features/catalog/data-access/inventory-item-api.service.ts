import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import {
  InventoryItemDto,
  InventoryItemPageDto,
  InventoryItemSearchQuery,
  RegisterInventoryItemRequest,
} from './inventory-item.dto';

/** Typed HTTP client for the catalog inventory-item endpoints. */
@Service()
export class InventoryItemApiService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly inventoryItemsUrl = buildInventoryItemsUrl(this.apiBaseUrl);

  search(query: InventoryItemSearchQuery): Observable<InventoryItemPageDto> {
    const name = query.name?.trim();
    let params = new HttpParams().set('page', query.page).set('size', query.size);

    if (name) {
      params = params.set('name', name);
    }

    if (query.category !== undefined) {
      params = params.set('category', query.category);
    }

    if (query.active !== undefined) {
      params = params.set('active', query.active);
    }

    return this.http.get<InventoryItemPageDto>(this.inventoryItemsUrl, { params });
  }

  getById(inventoryItemId: string): Observable<InventoryItemDto> {
    return this.http.get<InventoryItemDto>(`${this.inventoryItemsUrl}/${inventoryItemId}`);
  }

  register(request: RegisterInventoryItemRequest): Observable<InventoryItemDto> {
    return this.http.post<InventoryItemDto>(this.inventoryItemsUrl, request);
  }
}

function buildInventoryItemsUrl(apiBaseUrl: string): string {
  const normalizedBaseUrl = apiBaseUrl.replace(/\/+$/, '');
  const versionedBaseUrl = normalizedBaseUrl.endsWith('/api/v1')
    ? normalizedBaseUrl
    : `${normalizedBaseUrl}/api/v1`;

  return `${versionedBaseUrl}/inventory-items`;
}
