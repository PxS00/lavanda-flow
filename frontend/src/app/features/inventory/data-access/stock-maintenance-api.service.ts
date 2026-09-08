import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import { StockMaintenanceMovementDto, StockMaintenanceRequest } from './stock-maintenance.dto';

/** Typed HTTP client for backend-authoritative maintenance of one inventory batch. */
@Service()
export class StockMaintenanceApiService {
  private readonly http = inject(HttpClient);
  private readonly batchesUrl = buildBatchesUrl(inject(API_BASE_URL));

  adjust(
    batchId: string,
    request: StockMaintenanceRequest,
  ): Observable<StockMaintenanceMovementDto> {
    return this.post(batchId, 'adjustments', request);
  }

  registerLoss(
    batchId: string,
    request: StockMaintenanceRequest,
  ): Observable<StockMaintenanceMovementDto> {
    return this.post(batchId, 'losses', request);
  }

  registerExpiredDisposal(
    batchId: string,
    request: StockMaintenanceRequest,
  ): Observable<StockMaintenanceMovementDto> {
    return this.post(batchId, 'expired-disposals', request);
  }

  private post(
    batchId: string,
    operation: 'adjustments' | 'losses' | 'expired-disposals',
    request: StockMaintenanceRequest,
  ): Observable<StockMaintenanceMovementDto> {
    return this.http.post<StockMaintenanceMovementDto>(
      `${this.batchesUrl}/${batchId}/${operation}`,
      request,
    );
  }
}

function buildBatchesUrl(apiBaseUrl: string): string {
  const normalizedBaseUrl = apiBaseUrl.replace(/\/+$/, '');
  const versionedBaseUrl = normalizedBaseUrl.endsWith('/api/v1')
    ? normalizedBaseUrl
    : `${normalizedBaseUrl}/api/v1`;

  return `${versionedBaseUrl}/inventory/batches`;
}
