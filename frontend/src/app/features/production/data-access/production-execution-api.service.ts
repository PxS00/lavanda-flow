import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import {
  ProductionExecutionDetailsDto,
  ProductionExecutionDto,
  ProductionExecutionHistoryPageDto,
  ProductionExecutionHistoryQuery,
  RegisterProductionRequest,
} from './production-execution.dto';

/** Typed HTTP client for completed production registration and immutable history. */
@Service()
export class ProductionExecutionApiService {
  private readonly http = inject(HttpClient);
  private readonly executionsUrl = buildExecutionsUrl(inject(API_BASE_URL));

  register(request: RegisterProductionRequest): Observable<ProductionExecutionDto> {
    return this.http.post<ProductionExecutionDto>(this.executionsUrl, request);
  }

  search(query: ProductionExecutionHistoryQuery): Observable<ProductionExecutionHistoryPageDto> {
    let params = new HttpParams().set('page', query.page).set('size', query.size);
    if (query.from !== undefined) {
      params = params.set('from', query.from);
    }
    if (query.to !== undefined) {
      params = params.set('to', query.to);
    }
    return this.http.get<ProductionExecutionHistoryPageDto>(this.executionsUrl, { params });
  }

  getById(executionId: string): Observable<ProductionExecutionDetailsDto> {
    return this.http.get<ProductionExecutionDetailsDto>(
      `${this.executionsUrl}/${encodeURIComponent(executionId)}`,
    );
  }
}

function buildExecutionsUrl(apiBaseUrl: string): string {
  const normalizedBaseUrl = apiBaseUrl.replace(/\/+$/, '');
  const versionedBaseUrl = normalizedBaseUrl.endsWith('/api/v1')
    ? normalizedBaseUrl
    : `${normalizedBaseUrl}/api/v1`;

  return `${versionedBaseUrl}/production/executions`;
}
