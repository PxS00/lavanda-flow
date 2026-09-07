import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import { ProductionExecutionDto, RegisterProductionRequest } from './production-execution.dto';

/** Typed HTTP client for completed production registration. */
@Service()
export class ProductionExecutionApiService {
  private readonly http = inject(HttpClient);
  private readonly executionsUrl = buildExecutionsUrl(inject(API_BASE_URL));

  register(request: RegisterProductionRequest): Observable<ProductionExecutionDto> {
    return this.http.post<ProductionExecutionDto>(this.executionsUrl, request);
  }
}

function buildExecutionsUrl(apiBaseUrl: string): string {
  const normalizedBaseUrl = apiBaseUrl.replace(/\/+$/, '');
  const versionedBaseUrl = normalizedBaseUrl.endsWith('/api/v1')
    ? normalizedBaseUrl
    : `${normalizedBaseUrl}/api/v1`;

  return `${versionedBaseUrl}/production/executions`;
}
