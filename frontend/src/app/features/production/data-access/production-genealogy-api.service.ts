import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import { BatchGenealogyDto, GenealogyDirection } from './production-genealogy.dto';

@Service()
export class ProductionGenealogyApiService {
  private readonly http = inject(HttpClient);
  private readonly genealogyUrl = buildGenealogyUrl(inject(API_BASE_URL));

  getBatchGenealogy(
    batchId: string,
    direction: GenealogyDirection,
  ): Observable<BatchGenealogyDto> {
    return this.http.get<BatchGenealogyDto>(`${this.genealogyUrl}/batches/${batchId}`, {
      params: new HttpParams().set('direction', direction),
    });
  }
}

function buildGenealogyUrl(apiBaseUrl: string): string {
  const normalizedBaseUrl = apiBaseUrl.replace(/\/+$/, '');
  const versionedBaseUrl = normalizedBaseUrl.endsWith('/api/v1')
    ? normalizedBaseUrl
    : `${normalizedBaseUrl}/api/v1`;

  return `${versionedBaseUrl}/production/genealogy`;
}
