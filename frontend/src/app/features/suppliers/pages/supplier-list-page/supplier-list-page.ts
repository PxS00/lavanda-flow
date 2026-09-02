import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormField, form } from '@angular/forms/signals';
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
import { SupplierPageDto, SupplierSearchQuery } from '../../data-access/supplier.dto';
import { SupplierApiService } from '../../data-access/supplier-api.service';

const DEFAULT_PAGE_SIZE = 20;

interface SupplierFilters {
  readonly name: string;
  readonly active: 'all' | 'active' | 'inactive';
}

type SupplierListState =
  | { readonly kind: 'loading' }
  | { readonly kind: 'loaded'; readonly page: SupplierPageDto }
  | { readonly kind: 'error'; readonly error: UiError };

const DEFAULT_FILTERS: SupplierFilters = { name: '', active: 'all' };
const DEFAULT_QUERY: SupplierSearchQuery = { page: 0, size: DEFAULT_PAGE_SIZE };

@Component({
  selector: 'app-supplier-list-page',
  imports: [
    EmptyState,
    ErrorState,
    FormField,
    LoadingState,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    MatSelectModule,
    RouterLink,
  ],
  templateUrl: './supplier-list-page.html',
  styleUrl: './supplier-list-page.scss',
})
export class SupplierListPage {
  private readonly supplierApi = inject(SupplierApiService);
  private readonly requests = new Subject<SupplierSearchQuery>();
  private readonly currentQuery = signal<SupplierSearchQuery>(DEFAULT_QUERY);

  readonly filtersModel = signal<SupplierFilters>({ ...DEFAULT_FILTERS });
  protected readonly filtersForm = form(this.filtersModel);
  protected readonly state = signal<SupplierListState>({ kind: 'loading' });
  protected readonly hasAppliedFilters = computed(() => {
    const query = this.currentQuery();
    return query.name !== undefined || query.active !== undefined;
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
    const filters = this.filtersModel();
    const name = filters.name.trim();
    const query: SupplierSearchQuery = {
      page: 0,
      size: DEFAULT_PAGE_SIZE,
      ...(name ? { name } : {}),
      ...(filters.active === 'all' ? {} : { active: filters.active === 'active' }),
    };

    this.load(query);
  }

  protected resetFilters(): void {
    this.filtersForm().reset({ ...DEFAULT_FILTERS });
    this.load(DEFAULT_QUERY);
  }

  protected retry(): void {
    this.requests.next(this.currentQuery());
  }

  protected changePage(event: PageEvent): void {
    this.load({ ...this.currentQuery(), page: event.pageIndex, size: event.pageSize });
  }

  private load(query: SupplierSearchQuery): void {
    this.currentQuery.set(query);
    this.requests.next(query);
  }

  private search(query: SupplierSearchQuery) {
    return this.supplierApi.search(query).pipe(
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
          return this.supplierApi.search(correctedQuery);
        }

        return of(page);
      }),
      map((page): SupplierListState => ({ kind: 'loaded', page })),
      catchError((error: unknown) =>
        of<SupplierListState>({ kind: 'error', error: mapHttpError(error) }),
      ),
    );
  }
}
