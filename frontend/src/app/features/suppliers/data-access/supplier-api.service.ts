import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import {
  RegisterSupplierRequest,
  SupplierDto,
  SupplierPageDto,
  SupplierSearchQuery,
} from './supplier.dto';

/** Typed HTTP client for supplier management endpoints. */
@Service()
export class SupplierApiService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly suppliersUrl = buildSuppliersUrl(this.apiBaseUrl);

  search(query: SupplierSearchQuery): Observable<SupplierPageDto> {
    const name = query.name?.trim();
    let params = new HttpParams().set('page', query.page).set('size', query.size);

    if (name) {
      params = params.set('name', name);
    }

    if (query.active !== undefined) {
      params = params.set('active', query.active);
    }

    return this.http.get<SupplierPageDto>(this.suppliersUrl, { params });
  }

  getById(supplierId: string): Observable<SupplierDto> {
    return this.http.get<SupplierDto>(`${this.suppliersUrl}/${supplierId}`);
  }

  register(request: RegisterSupplierRequest): Observable<SupplierDto> {
    return this.http.post<SupplierDto>(this.suppliersUrl, request);
  }
}

function buildSuppliersUrl(apiBaseUrl: string): string {
  const normalizedBaseUrl = apiBaseUrl.replace(/\/+$/, '');
  const versionedBaseUrl = normalizedBaseUrl.endsWith('/api/v1')
    ? normalizedBaseUrl
    : `${normalizedBaseUrl}/api/v1`;

  return `${versionedBaseUrl}/suppliers`;
}
