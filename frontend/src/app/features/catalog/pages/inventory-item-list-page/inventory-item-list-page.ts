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

import { UiError } from '../../../../core/http/ui-error';
import { mapHttpError } from '../../../../core/http/map-http-error';
import { EmptyState } from '../../../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../../shared/ui/loading-state/loading-state';
import {
  InventoryItemCategory,
  InventoryItemPageDto,
  InventoryItemSearchQuery,
} from '../../data-access/inventory-item.dto';
import { InventoryItemApiService } from '../../data-access/inventory-item-api.service';
import {
  INVENTORY_ITEM_CATEGORY_OPTIONS,
  inventoryItemCategoryLabel,
  inventoryItemUnitLabel,
} from '../../inventory-item-display';

const DEFAULT_PAGE_SIZE = 20;

interface CatalogFilters {
  readonly name: string;
  readonly category: InventoryItemCategory | '';
  readonly active: 'all' | 'active' | 'inactive';
}

type CatalogListState =
  | { readonly kind: 'loading' }
  | { readonly kind: 'loaded'; readonly page: InventoryItemPageDto }
  | { readonly kind: 'error'; readonly error: UiError };

const DEFAULT_FILTERS: CatalogFilters = { name: '', category: '', active: 'all' };
const DEFAULT_QUERY: InventoryItemSearchQuery = { page: 0, size: DEFAULT_PAGE_SIZE };

@Component({
  selector: 'app-inventory-item-list-page',
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
  templateUrl: './inventory-item-list-page.html',
  styleUrl: './inventory-item-list-page.scss',
})
export class InventoryItemListPage {
  private readonly inventoryItemApi = inject(InventoryItemApiService);
  private readonly requests = new Subject<InventoryItemSearchQuery>();
  private readonly currentQuery = signal<InventoryItemSearchQuery>(DEFAULT_QUERY);

  readonly filtersModel = signal<CatalogFilters>({ ...DEFAULT_FILTERS });
  protected readonly filtersForm = form(this.filtersModel);
  protected readonly categoryOptions = INVENTORY_ITEM_CATEGORY_OPTIONS;
  protected readonly state = signal<CatalogListState>({ kind: 'loading' });
  protected readonly hasAppliedFilters = computed(() => {
    const query = this.currentQuery();
    return query.name !== undefined || query.category !== undefined || query.active !== undefined;
  });
  protected readonly categoryLabel = inventoryItemCategoryLabel;
  protected readonly unitLabel = inventoryItemUnitLabel;

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
    const query: InventoryItemSearchQuery = {
      page: 0,
      size: DEFAULT_PAGE_SIZE,
      ...(name ? { name } : {}),
      ...(filters.category ? { category: filters.category } : {}),
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

  private load(query: InventoryItemSearchQuery): void {
    this.currentQuery.set(query);
    this.requests.next(query);
  }

  private search(query: InventoryItemSearchQuery) {
    return this.inventoryItemApi.search(query).pipe(
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
          return this.inventoryItemApi.search(correctedQuery);
        }

        return of(page);
      }),
      map((page): CatalogListState => ({ kind: 'loaded', page })),
      catchError((error: unknown) =>
        of<CatalogListState>({ kind: 'error', error: mapHttpError(error) }),
      ),
    );
  }
}
