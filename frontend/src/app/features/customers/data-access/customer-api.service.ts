import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import { CustomerRequest, CustomerDto, CustomerPageDto, CustomerSearchQuery } from './customer.dto';

/** Typed HTTP client for customer management endpoints. */
@Service()
export class CustomerApiService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly customersUrl = buildCustomersUrl(this.apiBaseUrl);

  search(query: CustomerSearchQuery): Observable<CustomerPageDto> {
    const q = query.q?.trim();
    let params = new HttpParams().set('page', query.page).set('size', query.size);

    if (q) {
      params = params.set('q', q);
    }

    if (query.active !== undefined) {
      params = params.set('active', query.active);
    }

    return this.http.get<CustomerPageDto>(this.customersUrl, { params });
  }

  getById(customerId: string): Observable<CustomerDto> {
    return this.http.get<CustomerDto>(`${this.customersUrl}/${customerId}`);
  }

  register(request: CustomerRequest): Observable<CustomerDto> {
    return this.http.post<CustomerDto>(this.customersUrl, request);
  }

  update(customerId: string, request: CustomerRequest): Observable<CustomerDto> {
    return this.http.put<CustomerDto>(`${this.customersUrl}/${customerId}`, request);
  }
  activate(customerId: string): Observable<CustomerDto> {
    return this.http.post<CustomerDto>(`${this.customersUrl}/${customerId}/activate`, null);
  }

  deactivate(customerId: string): Observable<CustomerDto> {
    return this.http.post<CustomerDto>(`${this.customersUrl}/${customerId}/deactivate`, null);
  }
}

function buildCustomersUrl(apiBaseUrl: string): string {
  const normalizedBaseUrl = apiBaseUrl.replace(/\/+$/, '');
  const versionedBaseUrl = normalizedBaseUrl.endsWith('/api/v1')
    ? normalizedBaseUrl
    : `${normalizedBaseUrl}/api/v1`;

  return `${versionedBaseUrl}/customers`;
}
