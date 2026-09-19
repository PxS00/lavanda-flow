import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { ActivatedRoute, RouterLink, RouterLinkActive } from '@angular/router';
import { catchError, map, of, Subject, switchMap, tap } from 'rxjs';

import { mapHttpError } from '../../../../core/http/map-http-error';
import { UiError } from '../../../../core/http/ui-error';
import { formatDecimalString } from '../../../../core/i18n/decimal-string';
import { formatLocalDate } from '../../../../core/i18n/local-date';
import { EmptyState } from '../../../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../../shared/ui/loading-state/loading-state';
import {
  inventoryItemCategoryLabel,
  inventoryItemUnitLabel,
} from '../../../catalog/inventory-item-display';
import { InventoryStockListApiService } from '../../data-access/inventory-stock-list-api.service';
import {
  InventoryStockListEntryDto,
  InventoryStockListPageDto,
  InventoryStockListQuery,
} from '../../data-access/inventory-stock-list.dto';

type StockListState =
  | { readonly kind: 'loading' }
  | { readonly kind: 'loaded'; readonly page: InventoryStockListPageDto }
  | { readonly kind: 'error'; readonly error: UiError };

@Component({
  selector: 'app-inventory-stock-list-page',
  imports: [
    EmptyState,
    ErrorState,
    LoadingState,
    MatButtonModule,
    MatPaginatorModule,
    RouterLink,
    RouterLinkActive,
  ],
  templateUrl: './inventory-stock-list-page.html',
  styleUrl: './inventory-stock-list-page.scss',
})
export class InventoryStockListPage {
  private readonly api = inject(InventoryStockListApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly requests = new Subject<InventoryStockListQuery>();
  private readonly categories = this.route.snapshot.data['categories'] ?? [];
  private readonly currentQuery = signal<InventoryStockListQuery>({
    categories: this.categories,
    page: 0,
    size: 20,
  });

  protected readonly title: string = this.route.snapshot.data['title'];
  protected readonly helperText: string = this.route.snapshot.data['helperText'];
  protected readonly state = signal<StockListState>({ kind: 'loading' });
  protected readonly formatDecimal = formatDecimalString;
  protected readonly formatDate = formatLocalDate;
  protected readonly categoryLabel = inventoryItemCategoryLabel;
  protected readonly unitLabel = inventoryItemUnitLabel;

  constructor() {
    this.requests
      .pipe(
        tap(() => this.state.set({ kind: 'loading' })),
        switchMap((query) =>
          this.api.search(query).pipe(
            map((page): StockListState => ({ kind: 'loaded', page })),
            catchError((error: unknown) =>
              of<StockListState>({ kind: 'error', error: mapHttpError(error) }),
            ),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((state) => this.state.set(state));
    this.requests.next(this.currentQuery());
  }

  protected retry(): void {
    this.requests.next(this.currentQuery());
  }

  protected changePage(event: PageEvent): void {
    const query = { ...this.currentQuery(), page: event.pageIndex, size: event.pageSize };
    this.currentQuery.set(query);
    this.requests.next(query);
  }

  protected stockStatus(item: InventoryStockListEntryDto): string {
    if (item.outOfStock) return 'Sem estoque';
    if (item.lowStock) return 'Estoque baixo';
    return 'Estoque regular';
  }
}
