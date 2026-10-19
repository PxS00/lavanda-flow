import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { RouterLink } from '@angular/router';
import { Subject, catchError, map, of, startWith, switchMap, tap } from 'rxjs';

import { mapHttpError } from '../../../../core/http/map-http-error';
import { UiError } from '../../../../core/http/ui-error';
import { EmptyState } from '../../../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../../shared/ui/loading-state/loading-state';
import { CustomerPageDto, CustomerSearchQuery } from '../../data-access/customer.dto';
import { CustomerApiService } from '../../data-access/customer-api.service';

const DEFAULT_PAGE_SIZE = 20;

interface CustomerFilters {
  readonly q: string;
  readonly active: 'all' | 'active' | 'inactive';
}

type CustomerListState =
  | { readonly kind: 'loading' }
  | { readonly kind: 'loaded'; readonly page: CustomerPageDto }
  | { readonly kind: 'error'; readonly error: UiError };

const DEFAULT_FILTERS: CustomerFilters = { q: '', active: 'active' };
const DEFAULT_QUERY: CustomerSearchQuery = { page: 0, size: DEFAULT_PAGE_SIZE, active: true };

@Component({
  selector: 'app-customer-list-page',
  imports: [
    EmptyState,
    ErrorState,
    ReactiveFormsModule,
    LoadingState,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    MatSelectModule,
    RouterLink,
  ],
  templateUrl: './customer-list-page.html',
  styleUrl: './customer-list-page.scss',
})
export class CustomerListPage {
  private readonly customerApi = inject(CustomerApiService);
  private readonly requests = new Subject<CustomerSearchQuery>();
  private readonly currentQuery = signal<CustomerSearchQuery>(DEFAULT_QUERY);

  readonly filtersForm = new FormGroup({
    q: new FormControl('', { nonNullable: true }),
    active: new FormControl<CustomerFilters['active']>('active', { nonNullable: true }),
  });
  protected readonly state = signal<CustomerListState>({ kind: 'loading' });
  protected readonly hasAppliedFilters = computed(() => {
    const query = this.currentQuery();
    return query.q !== undefined || query.active !== true;
  });

  constructor() {
    this.requests
      .pipe(
        startWith(DEFAULT_QUERY),
        tap(() => this.state.set({ kind: 'loading' })),
        switchMap((query) => this.search(query)),
        takeUntilDestroyed(),
      )
      .subscribe((state) => this.state.set(state));
  }

  protected applyFilters(event: SubmitEvent): void {
    event.preventDefault();
    const filters = this.filtersForm.getRawValue();
    const q = filters.q.trim();
    const query: CustomerSearchQuery = {
      page: 0,
      size: DEFAULT_PAGE_SIZE,
      ...(q ? { q } : {}),
      ...(filters.active === 'all' ? {} : { active: filters.active === 'active' }),
    };

    this.load(query);
  }

  protected resetFilters(): void {
    this.filtersForm.reset({ ...DEFAULT_FILTERS });
    this.load(DEFAULT_QUERY);
  }

  protected retry(): void {
    this.requests.next(this.currentQuery());
  }

  protected changePage(event: PageEvent): void {
    this.load({ ...this.currentQuery(), page: event.pageIndex, size: event.pageSize });
  }

  private load(query: CustomerSearchQuery): void {
    this.currentQuery.set(query);
    this.requests.next(query);
  }

  private search(query: CustomerSearchQuery) {
    return this.customerApi.search(query).pipe(
      switchMap((page) => {
        const lastValidPage = page.totalPages - 1;
        if (
          page.content.length === 0 &&
          query.page > 0 &&
          page.totalElements > 0 &&
          page.totalPages > 0 &&
          lastValidPage !== query.page
        ) {
          const correctedQuery = { ...query, page: lastValidPage };
          this.currentQuery.set(correctedQuery);
          return this.customerApi.search(correctedQuery);
        }

        return of(page);
      }),
      map((page): CustomerListState => ({ kind: 'loaded', page })),
      catchError((error: unknown) =>
        of<CustomerListState>({ kind: 'error', error: mapHttpError(error) }),
      ),
    );
  }
}
