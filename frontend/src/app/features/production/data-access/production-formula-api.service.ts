import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import { ProductionFormulaDto, UpsertProductionFormulaRequest } from './production-formula.dto';

/** Typed HTTP client for the production formula setup endpoints. */
@Service()
export class ProductionFormulaApiService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly formulasUrl = buildFormulasUrl(this.apiBaseUrl);

  list(): Observable<readonly ProductionFormulaDto[]> {
    return this.http.get<readonly ProductionFormulaDto[]>(this.formulasUrl);
  }

  getById(formulaId: string): Observable<ProductionFormulaDto> {
    return this.http.get<ProductionFormulaDto>(`${this.formulasUrl}/${formulaId}`);
  }

  create(request: UpsertProductionFormulaRequest): Observable<ProductionFormulaDto> {
    return this.http.post<ProductionFormulaDto>(this.formulasUrl, request);
  }

  update(formulaId: string, request: UpsertProductionFormulaRequest): Observable<ProductionFormulaDto> {
    return this.http.put<ProductionFormulaDto>(`${this.formulasUrl}/${formulaId}`, request);
  }
}

function buildFormulasUrl(apiBaseUrl: string): string {
  const normalizedBaseUrl = apiBaseUrl.replace(/\/+$/, '');
  const versionedBaseUrl = normalizedBaseUrl.endsWith('/api/v1')
    ? normalizedBaseUrl
    : `${normalizedBaseUrl}/api/v1`;

  return `${versionedBaseUrl}/production/formulas`;
}
