import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import { RegisterStockReceiptDto, RegisterStockReceiptRequest } from './stock-receipt.dto';

/** Typed HTTP client for the transactional stock-receipt endpoint. */
@Service()
export class StockReceiptApiService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly receiptsUrl = buildReceiptsUrl(this.apiBaseUrl);

  register(request: RegisterStockReceiptRequest): Observable<RegisterStockReceiptDto> {
    return this.http.post<RegisterStockReceiptDto>(this.receiptsUrl, request);
  }
}

function buildReceiptsUrl(apiBaseUrl: string): string {
  const normalizedBaseUrl = apiBaseUrl.replace(/\/+$/, '');
  const versionedBaseUrl = normalizedBaseUrl.endsWith('/api/v1')
    ? normalizedBaseUrl
    : `${normalizedBaseUrl}/api/v1`;

  return `${versionedBaseUrl}/inventory/receipts`;
}
