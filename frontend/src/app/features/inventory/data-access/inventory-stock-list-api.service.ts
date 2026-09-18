import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import { InventoryStockListPageDto, InventoryStockListQuery } from './inventory-stock-list.dto';

@Service()
export class InventoryStockListApiService {
  private readonly http = inject(HttpClient);
  private readonly url = buildUrl(inject(API_BASE_URL));

  search(query: InventoryStockListQuery): Observable<InventoryStockListPageDto> {
    let params = new HttpParams().set('page', query.page).set('size', query.size);
    query.categories.forEach((category) => (params = params.append('category', category)));
    return this.http.get<InventoryStockListPageDto>(this.url, { params });
  }
}

function buildUrl(apiBaseUrl: string): string {
  const normalizedBaseUrl = apiBaseUrl.replace(/\/+$/, '');
  const versionedBaseUrl = normalizedBaseUrl.endsWith('/api/v1')
    ? normalizedBaseUrl
    : `${normalizedBaseUrl}/api/v1`;
  return `${versionedBaseUrl}/inventory/items`;
}
