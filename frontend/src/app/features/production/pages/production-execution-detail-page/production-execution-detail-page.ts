import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subject, catchError, map, of, switchMap, tap } from 'rxjs';

import { mapHttpError } from '../../../../core/http/map-http-error';
import { UiError } from '../../../../core/http/ui-error';
import { formatDecimalString } from '../../../../core/i18n/decimal-string';
import { formatLocalDate } from '../../../../core/i18n/local-date';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../../shared/ui/loading-state/loading-state';
import { inventoryItemUnitLabel } from '../../../catalog/inventory-item-display';
import { ProductionExecutionApiService } from '../../data-access/production-execution-api.service';
import {
  ProductionExecutionDetailsDto,
  ProductionLotCodeMode,
} from '../../data-access/production-execution.dto';

type DetailState =
  | { readonly kind: 'loading' }
  | { readonly kind: 'loaded'; readonly execution: ProductionExecutionDetailsDto }
  | { readonly kind: 'error'; readonly error: UiError };

@Component({
  selector: 'app-production-execution-detail-page',
  imports: [DatePipe, ErrorState, LoadingState, MatButtonModule, MatCardModule, RouterLink],
  templateUrl: './production-execution-detail-page.html',
  styleUrl: './production-execution-detail-page.scss',
})
export class ProductionExecutionDetailPage {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(ProductionExecutionApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly requests = new Subject<string>();
  private readonly executionId = signal<string | null>(null);

  protected readonly state = signal<DetailState>({ kind: 'loading' });
  protected readonly formatDecimal = formatDecimalString;
  protected readonly formatLocalDate = formatLocalDate;
  protected readonly unitLabel = inventoryItemUnitLabel;

  constructor() {
    this.requests
      .pipe(
        tap(() => this.state.set({ kind: 'loading' })),
        switchMap((executionId) =>
          this.api.getById(executionId).pipe(
            map((execution): DetailState => ({ kind: 'loaded', execution })),
            catchError((error: unknown) =>
              of<DetailState>({ kind: 'error', error: mapHttpError(error) }),
            ),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((state) => this.state.set(state));

    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const executionId = params.get('executionId')?.trim() ?? '';
      if (executionId === '') {
        this.executionId.set(null);
        this.state.set({
          kind: 'error',
          error: { kind: 'validation', message: 'ID da execução não informado.' },
        });
        return;
      }
      this.executionId.set(executionId);
      this.requests.next(executionId);
    });
  }

  protected retry(): void {
    const executionId = this.executionId();
    if (executionId !== null) {
      this.requests.next(executionId);
    }
  }

  protected lotCodeModeLabel(mode: ProductionLotCodeMode): string {
    return mode === 'GENERATED' ? 'Gerado pelo sistema' : 'Informado manualmente';
  }
}
