import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { RouterLink } from '@angular/router';
import { Subject, catchError, map, of, startWith, switchMap, tap } from 'rxjs';
import { mapHttpError } from '../../../core/http/map-http-error';
import { UiError } from '../../../core/http/ui-error';
import { formatDecimalString } from '../../../core/i18n/decimal-string';
import { EmptyState } from '../../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../shared/ui/loading-state/loading-state';
import { OrderApiService } from '../data-access/order-api.service';
import { OrderPageDto, OrderSearchQuery } from '../data-access/order.dto';

type ListState =
  { kind: 'loading' } | { kind: 'loaded'; page: OrderPageDto } | { kind: 'error'; error: UiError };
@Component({
  selector: 'app-order-list-page',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    EmptyState,
    ErrorState,
    LoadingState,
  ],
  templateUrl: './order-list-page.html',
  styleUrl: './sales-pages.scss',
})
export class OrderListPage {
  private readonly api = inject(OrderApiService);
  private readonly requests = new Subject<OrderSearchQuery>();
  private query: OrderSearchQuery = { page: 0, size: 20 };
  readonly filters = new FormGroup({
    q: new FormControl('', { nonNullable: true }),
    from: new FormControl('', { nonNullable: true }),
    to: new FormControl('', { nonNullable: true }),
  });
  protected readonly state = signal<ListState>({ kind: 'loading' });
  protected readonly filterError = signal<string | null>(null);
  protected readonly formatDecimal = formatDecimalString;
  constructor() {
    this.requests
      .pipe(
        startWith(this.query),
        tap(() => this.state.set({ kind: 'loading' })),
        switchMap((query) =>
          this.api.search(query).pipe(
            switchMap((page) => {
              if (page.content.length === 0 && query.page > 0 && page.totalPages > 0) {
                this.query = { ...query, page: page.totalPages - 1 };
                return this.api.search(this.query);
              }
              return of(page);
            }),
            map((page): ListState => ({ kind: 'loaded', page })),
            catchError((error: unknown) =>
              of<ListState>({ kind: 'error', error: mapHttpError(error) }),
            ),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((state) => this.state.set(state));
  }
  protected applyFilters(): void {
    const value = this.filters.getRawValue();
    if (value.from && value.to && value.from > value.to) {
      this.filterError.set('A data inicial deve ser anterior ou igual à data final.');
      return;
    }
    this.filterError.set(null);
    this.query = {
      page: 0,
      size: this.query.size,
      q: value.q.trim() || undefined,
      from: value.from || undefined,
      to: value.to || undefined,
    };
    this.retry();
  }
  protected clearFilters(): void {
    this.filters.reset();
    this.applyFilters();
  }
  protected changePage(event: PageEvent): void {
    this.query = { ...this.query, page: event.pageIndex, size: event.pageSize };
    this.retry();
  }
  protected retry(): void {
    this.requests.next(this.query);
  }
}
