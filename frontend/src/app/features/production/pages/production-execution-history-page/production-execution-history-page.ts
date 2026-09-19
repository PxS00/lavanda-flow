import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { RouterLink } from '@angular/router';
import { Subject, catchError, map, of, startWith, switchMap, tap } from 'rxjs';

import { mapHttpError } from '../../../../core/http/map-http-error';
import { UiError } from '../../../../core/http/ui-error';
import { formatDecimalString } from '../../../../core/i18n/decimal-string';
import { formatLocalDate } from '../../../../core/i18n/local-date';
import { EmptyState } from '../../../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../../shared/ui/loading-state/loading-state';
import { inventoryItemUnitLabel } from '../../../catalog/inventory-item-display';
import { ProductionExecutionApiService } from '../../data-access/production-execution-api.service';
import {
  ProductionExecutionHistoryPageDto,
  ProductionExecutionHistoryQuery,
} from '../../data-access/production-execution.dto';

const DEFAULT_PAGE_SIZE = 20;

type HistoryState =
  | { readonly kind: 'loading' }
  | { readonly kind: 'loaded'; readonly page: ProductionExecutionHistoryPageDto }
  | { readonly kind: 'error'; readonly error: UiError };

@Component({
  selector: 'app-production-execution-history-page',
  imports: [
    EmptyState,
    ErrorState,
    LoadingState,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    ReactiveFormsModule,
    RouterLink,
  ],
  templateUrl: './production-execution-history-page.html',
  styleUrl: './production-execution-history-page.scss',
})
export class ProductionExecutionHistoryPage {
  private readonly api = inject(ProductionExecutionApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly requests = new Subject<ProductionExecutionHistoryQuery>();
  private readonly currentQuery = signal<ProductionExecutionHistoryQuery>({
    page: 0,
    size: DEFAULT_PAGE_SIZE,
  });

  protected readonly filters = new FormGroup({
    from: new FormControl('', { nonNullable: true }),
    to: new FormControl('', { nonNullable: true }),
  });
  protected readonly filterError = signal<string | null>(null);
  protected readonly state = signal<HistoryState>({ kind: 'loading' });
  protected readonly formatDecimal = formatDecimalString;
  protected readonly formatLocalDate = formatLocalDate;
  protected readonly unitLabel = inventoryItemUnitLabel;

  constructor() {
    this.requests
      .pipe(
        startWith(this.currentQuery()),
        tap(() => this.state.set({ kind: 'loading' })),
        switchMap((query) =>
          this.api.search(query).pipe(
            map((page): HistoryState => ({ kind: 'loaded', page })),
            catchError((error: unknown) =>
              of<HistoryState>({ kind: 'error', error: mapHttpError(error) }),
            ),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((state) => this.state.set(state));
  }

  protected applyFilters(): void {
    const from = this.filters.controls.from.value;
    const to = this.filters.controls.to.value;
    if (from !== '' && to !== '' && from > to) {
      this.filterError.set('A data inicial deve ser anterior ou igual à data final.');
      return;
    }

    this.filterError.set(null);
    this.load({
      from: from === '' ? undefined : from,
      to: to === '' ? undefined : to,
      page: 0,
      size: this.currentQuery().size,
    });
  }

  protected clearFilters(): void {
    this.filters.reset({ from: '', to: '' });
    this.filterError.set(null);
    this.load({ page: 0, size: this.currentQuery().size });
  }

  protected changePage(event: PageEvent): void {
    this.load({ ...this.currentQuery(), page: event.pageIndex, size: event.pageSize });
  }

  protected retry(): void {
    this.requests.next(this.currentQuery());
  }

  private load(query: ProductionExecutionHistoryQuery): void {
    this.currentQuery.set(query);
    this.requests.next(query);
  }
}
