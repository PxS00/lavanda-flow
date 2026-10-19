import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import {
  OrderDto,
  OrderPageDto,
  OrderSearchQuery,
  SaveDraftRequest,
  RegisterSaleStockCorrection,
  SaleStockCorrectionDto,
} from './order.dto';

/** Exact-decimal DTO client; authentication and CSRF use the existing HTTP interceptors. */
@Service()
export class OrderApiService {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL).replace(/\/+$/, '');
  private readonly url = `${this.base.endsWith('/api/v1') ? this.base : this.base + '/api/v1'}/sales`;

  search(query: OrderSearchQuery) {
    let params = new HttpParams().set('page', query.page).set('size', query.size);
    for (const key of [
      'q',
      'customerId',
      'from',
      'to',
      'status',
      'confirmedFrom',
      'confirmedTo',
    ] as const) {
      const value = query[key]?.trim();
      if (value) params = params.set(key, value);
    }
    return this.http.get<OrderPageDto>(this.url, { params });
  }
  getById(id: string) {
    return this.http.get<OrderDto>(`${this.url}/${id}`);
  }
  confirm(id: string) {
    return this.http.post<OrderDto>(`${this.url}/${id}/confirm`, null);
  }
  stockCorrections(id: string) {
    return this.http.get<readonly SaleStockCorrectionDto[]>(`${this.url}/${id}/stock-corrections`);
  }
  correctStock(id: string, correctionId: string, request: RegisterSaleStockCorrection) {
    return this.http.put<SaleStockCorrectionDto>(
      `${this.url}/${id}/stock-corrections/${correctionId}`,
      request,
    );
  }
  register(request: SaveDraftRequest) {
    return this.http.post<OrderDto>(this.url, request);
  }
  update(id: string, request: SaveDraftRequest) {
    return this.http.put<OrderDto>(`${this.url}/${id}`, request);
  }
}
