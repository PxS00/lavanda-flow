import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  Subject,
  catchError,
  distinctUntilChanged,
  map,
  of,
  startWith,
  switchMap,
  tap,
} from 'rxjs';

import { mapHttpError } from '../../../../core/http/map-http-error';
import { UiError } from '../../../../core/http/ui-error';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../../shared/ui/loading-state/loading-state';
import { InventoryItemDto } from '../../data-access/inventory-item.dto';
import { InventoryItemApiService } from '../../data-access/inventory-item-api.service';
import { inventoryItemCategoryLabel, inventoryItemUnitLabel } from '../../inventory-item-display';

type InventoryItemDetailState =
  | { readonly kind: 'loading' }
  | { readonly kind: 'loaded'; readonly item: InventoryItemDto }
  | { readonly kind: 'error'; readonly error: UiError };

@Component({
  selector: 'app-inventory-item-detail-page',
  imports: [ErrorState, LoadingState, MatButtonModule, MatCardModule, RouterLink],
  templateUrl: './inventory-item-detail-page.html',
  styleUrl: './inventory-item-detail-page.scss',
})
export class InventoryItemDetailPage {
  private readonly route = inject(ActivatedRoute);
  private readonly inventoryItemApi = inject(InventoryItemApiService);
  private readonly retries = new Subject<void>();

  protected readonly state = signal<InventoryItemDetailState>({ kind: 'loading' });
  protected readonly categoryLabel = inventoryItemCategoryLabel;
  protected readonly unitLabel = inventoryItemUnitLabel;

  constructor() {
    this.route.paramMap
      .pipe(
        map((params) => params.get('inventoryItemId') ?? ''),
        distinctUntilChanged(),
        switchMap((inventoryItemId) =>
          this.retries.pipe(
            startWith(undefined),
            tap(() => this.state.set({ kind: 'loading' })),
            switchMap(() =>
              this.inventoryItemApi.getById(inventoryItemId).pipe(
                map((item): InventoryItemDetailState => ({ kind: 'loaded', item })),
                catchError((error: unknown) =>
                  of<InventoryItemDetailState>({ kind: 'error', error: mapHttpError(error) }),
                ),
              ),
            ),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((state) => this.state.set(state));
  }

  protected retry(): void {
    this.retries.next();
  }
}
