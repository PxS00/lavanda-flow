import { DecimalPipe } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormField, form, required, validate } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  Observable,
  Subject,
  catchError,
  distinctUntilChanged,
  filter,
  finalize,
  map,
  of,
  switchMap,
  tap,
} from 'rxjs';

import { mapHttpError } from '../../../../core/http/map-http-error';
import { UiError } from '../../../../core/http/ui-error';
import { EmptyState } from '../../../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../../shared/ui/loading-state/loading-state';
import { InventoryItemOperationsApiService } from '../../data-access/inventory-item-operations-api.service';
import {
  BatchInventoryDto,
  BatchOperationalStatus,
  InventoryItemMovementHistoryQuery,
  InventoryItemOverviewDto,
  InventoryMovementType,
  MinimumStockLevelDto,
  MovementHistoryPageDto,
} from '../../data-access/inventory-operations.dto';
import { MovementHistoryApiService } from '../../data-access/movement-history-api.service';

const DEFAULT_MOVEMENT_PAGE_SIZE = 20;
const MINIMUM_QUANTITY_PATTERN = /^\d+(?:\.\d{1,6})?$/;

interface MinimumStockFormModel {
  readonly minimumQuantity: string;
}

type PanelState<T> =
  | { readonly kind: 'loading' }
  | { readonly kind: 'loaded'; readonly data: T }
  | { readonly kind: 'error'; readonly error: UiError };

@Component({
  selector: 'app-inventory-item-operational-page',
  imports: [
    DecimalPipe,
    EmptyState,
    ErrorState,
    FormField,
    LoadingState,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    RouterLink,
  ],
  templateUrl: './inventory-item-operational-page.html',
  styleUrl: './inventory-item-operational-page.scss',
})
export class InventoryItemOperationalPage {
  private readonly route = inject(ActivatedRoute);
  private readonly operationsApi = inject(InventoryItemOperationsApiService);
  private readonly movementHistoryApi = inject(MovementHistoryApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly overviewRequests = new Subject<string>();
  private readonly batchRequests = new Subject<string>();
  private readonly minimumRequests = new Subject<string>();
  private readonly movementRequests = new Subject<InventoryItemMovementHistoryQuery>();
  private readonly currentMovementQuery = signal<InventoryItemMovementHistoryQuery | null>(null);

  readonly inventoryItemId = signal<string | null>(null);
  readonly minimumStockModel = signal<MinimumStockFormModel>({ minimumQuantity: '' });
  protected readonly minimumStockForm = form(this.minimumStockModel, (minimum) => {
    required(minimum.minimumQuantity, { message: 'Minimum quantity is required.' });
    validate(minimum.minimumQuantity, ({ value }) => {
      const rawValue = value();
      const normalized = rawValue.trim();

      if (rawValue.length > 0 && normalized.length === 0) {
        return { kind: 'blank', message: 'Minimum quantity is required.' };
      }

      if (normalized.length === 0) {
        return undefined;
      }

      if (!MINIMUM_QUANTITY_PATTERN.test(normalized) || Number(normalized) <= 0) {
        return {
          kind: 'positive-decimal',
          message: 'Use a positive number with up to 6 decimal places.',
        };
      }

      return undefined;
    });
  });

  protected readonly overviewState = signal<PanelState<InventoryItemOverviewDto>>({
    kind: 'loading',
  });
  protected readonly batchState = signal<PanelState<BatchInventoryDto>>({ kind: 'loading' });
  protected readonly minimumState = signal<PanelState<MinimumStockLevelDto | null>>({
    kind: 'loading',
  });
  protected readonly movementState = signal<PanelState<MovementHistoryPageDto>>({
    kind: 'loading',
  });
  protected readonly minimumActionError = signal<UiError | null>(null);
  protected readonly minimumNotice = signal<string | null>(null);
  protected readonly isSavingMinimum = signal(false);
  protected readonly isRemovingMinimum = signal(false);
  protected readonly confirmingMinimumRemoval = signal(false);
  protected readonly minimumGlobalActionError = computed(() => {
    const error = this.minimumActionError();
    if (error === null || error.fieldErrors?.['minimumQuantity'] === undefined) {
      return error;
    }

    const remainingFieldErrors = Object.fromEntries(
      Object.entries(error.fieldErrors).filter(([field]) => field !== 'minimumQuantity'),
    );

    return Object.keys(remainingFieldErrors).length > 0
      ? { ...error, fieldErrors: remainingFieldErrors }
      : null;
  });
  protected readonly enumLabel = formatEnumLabel;
  protected readonly batchStatusLabel = batchStatusLabel;
  protected readonly movementTypeLabel = movementTypeLabel;
  protected readonly stockStatusLabel = stockStatusLabel;

  constructor() {
    this.bindOverviewRequests();
    this.bindBatchRequests();
    this.bindMinimumRequests();
    this.bindMovementRequests();

    this.route.paramMap
      .pipe(
        map((params) => params.get('inventoryItemId')),
        filter((inventoryItemId): inventoryItemId is string => inventoryItemId !== null),
        distinctUntilChanged(),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((inventoryItemId) => this.loadInventoryItem(inventoryItemId));
  }

  protected retryOverview(): void {
    this.emitForCurrentItem(this.overviewRequests);
  }

  protected retryBatches(): void {
    this.emitForCurrentItem(this.batchRequests);
  }

  protected retryMinimumStock(): void {
    this.emitForCurrentItem(this.minimumRequests);
  }

  protected retryMovements(): void {
    const query = this.currentMovementQuery();
    if (query !== null) {
      this.movementRequests.next(query);
    }
  }

  protected changeMovementPage(event: PageEvent): void {
    const query = this.currentMovementQuery();
    if (query === null) {
      return;
    }

    this.loadMovements({ ...query, page: event.pageIndex, size: event.pageSize });
  }

  protected saveMinimumStock(event: SubmitEvent): void {
    event.preventDefault();
    if (this.isSavingMinimum() || this.isRemovingMinimum()) {
      return;
    }

    this.minimumStockForm().markAsTouched();
    if (this.minimumStockForm().invalid()) {
      this.minimumStockForm.minimumQuantity().focusBoundControl();
      return;
    }

    const inventoryItemId = this.inventoryItemId();
    if (inventoryItemId === null) {
      return;
    }

    const minimumQuantity = Number(this.minimumStockModel().minimumQuantity.trim());
    this.isSavingMinimum.set(true);
    this.minimumActionError.set(null);
    this.minimumNotice.set(null);

    this.operationsApi
      .configureMinimumStockLevel(inventoryItemId, { minimumQuantity })
      .pipe(
        finalize(() => this.isSavingMinimum.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (level) => {
          if (this.inventoryItemId() !== inventoryItemId) {
            return;
          }

          this.minimumState.set({ kind: 'loaded', data: level });
          this.minimumStockForm().reset({ minimumQuantity: String(level.minimumQuantity) });
          this.confirmingMinimumRemoval.set(false);
          this.minimumNotice.set('Minimum stock level saved.');
          this.overviewRequests.next(inventoryItemId);
        },
        error: (error: unknown) => this.minimumActionError.set(mapHttpError(error)),
      });
  }

  protected requestMinimumRemoval(): void {
    this.minimumActionError.set(null);
    this.minimumNotice.set(null);
    this.confirmingMinimumRemoval.set(true);
  }

  protected cancelMinimumRemoval(): void {
    this.confirmingMinimumRemoval.set(false);
  }

  protected confirmMinimumRemoval(): void {
    if (this.isRemovingMinimum() || this.isSavingMinimum()) {
      return;
    }

    const inventoryItemId = this.inventoryItemId();
    if (inventoryItemId === null) {
      return;
    }

    this.isRemovingMinimum.set(true);
    this.minimumActionError.set(null);
    this.minimumNotice.set(null);

    this.operationsApi
      .deleteMinimumStockLevel(inventoryItemId)
      .pipe(
        finalize(() => this.isRemovingMinimum.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          if (this.inventoryItemId() !== inventoryItemId) {
            return;
          }

          this.minimumState.set({ kind: 'loaded', data: null });
          this.minimumStockForm().reset({ minimumQuantity: '' });
          this.confirmingMinimumRemoval.set(false);
          this.minimumNotice.set('Minimum stock level removed.');
          this.overviewRequests.next(inventoryItemId);
        },
        error: (error: unknown) => this.minimumActionError.set(mapHttpError(error)),
      });
  }

  protected backendMinimumError(): string | undefined {
    return this.minimumActionError()?.fieldErrors?.['minimumQuantity'];
  }

  private loadInventoryItem(inventoryItemId: string): void {
    this.inventoryItemId.set(inventoryItemId);
    this.minimumStockForm().reset({ minimumQuantity: '' });
    this.minimumActionError.set(null);
    this.minimumNotice.set(null);
    this.confirmingMinimumRemoval.set(false);

    this.overviewRequests.next(inventoryItemId);
    this.batchRequests.next(inventoryItemId);
    this.minimumRequests.next(inventoryItemId);
    this.loadMovements({ inventoryItemId, page: 0, size: DEFAULT_MOVEMENT_PAGE_SIZE });
  }

  private loadMovements(query: InventoryItemMovementHistoryQuery): void {
    this.currentMovementQuery.set(query);
    this.movementRequests.next(query);
  }

  private emitForCurrentItem(requests: Subject<string>): void {
    const inventoryItemId = this.inventoryItemId();
    if (inventoryItemId !== null) {
      requests.next(inventoryItemId);
    }
  }

  private bindOverviewRequests(): void {
    this.overviewRequests
      .pipe(
        tap(() => this.overviewState.set({ kind: 'loading' })),
        switchMap((inventoryItemId) =>
          this.operationsApi.getOverview(inventoryItemId).pipe(
            map(
              (data): PanelState<InventoryItemOverviewDto> => ({ kind: 'loaded', data }),
            ),
            catchError((error: unknown) =>
              of<PanelState<InventoryItemOverviewDto>>({
                kind: 'error',
                error: mapHttpError(error),
              }),
            ),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((state) => this.overviewState.set(state));
  }

  private bindBatchRequests(): void {
    this.batchRequests
      .pipe(
        tap(() => this.batchState.set({ kind: 'loading' })),
        switchMap((inventoryItemId) =>
          this.operationsApi.getBatches(inventoryItemId).pipe(
            map((data): PanelState<BatchInventoryDto> => ({ kind: 'loaded', data })),
            catchError((error: unknown) =>
              of<PanelState<BatchInventoryDto>>({ kind: 'error', error: mapHttpError(error) }),
            ),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((state) => this.batchState.set(state));
  }

  private bindMinimumRequests(): void {
    this.minimumRequests
      .pipe(
        tap(() => this.minimumState.set({ kind: 'loading' })),
        switchMap((inventoryItemId) =>
          this.operationsApi.getMinimumStockLevel(inventoryItemId).pipe(
            map((data): PanelState<MinimumStockLevelDto | null> => {
              this.minimumStockForm().reset({ minimumQuantity: String(data.minimumQuantity) });
              return { kind: 'loaded', data };
            }),
            catchError((error: unknown) => {
              const mappedError = mapHttpError(error);
              if (
                mappedError.kind === 'not-found' &&
                mappedError.code === 'MINIMUM_STOCK_LEVEL_NOT_FOUND'
              ) {
                this.minimumStockForm().reset({ minimumQuantity: '' });
                return of<PanelState<MinimumStockLevelDto | null>>({
                  kind: 'loaded',
                  data: null,
                });
              }

              return of<PanelState<MinimumStockLevelDto | null>>({
                kind: 'error',
                error: mappedError,
              });
            }),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((state) => this.minimumState.set(state));
  }

  private bindMovementRequests(): void {
    this.movementRequests
      .pipe(
        tap(() => this.movementState.set({ kind: 'loading' })),
        switchMap((query) => this.searchMovements(query)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((state) => this.movementState.set(state));
  }

  private searchMovements(
    query: InventoryItemMovementHistoryQuery,
  ): Observable<PanelState<MovementHistoryPageDto>> {
    return this.movementHistoryApi.search(query).pipe(
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
          this.currentMovementQuery.set(correctedQuery);
          return this.movementHistoryApi.search(correctedQuery);
        }

        return of(page);
      }),
      map((data): PanelState<MovementHistoryPageDto> => ({ kind: 'loaded', data })),
      catchError((error: unknown) =>
        of<PanelState<MovementHistoryPageDto>>({ kind: 'error', error: mapHttpError(error) }),
      ),
    );
  }
}

function formatEnumLabel(value: string): string {
  const normalized = value.toLowerCase().replace(/_/g, ' ');
  return normalized.charAt(0).toUpperCase() + normalized.slice(1);
}

function batchStatusLabel(status: BatchOperationalStatus): string {
  return formatEnumLabel(status);
}

function movementTypeLabel(type: InventoryMovementType): string {
  return formatEnumLabel(type);
}

function stockStatusLabel(overview: InventoryItemOverviewDto): string {
  if (overview.outOfStock) {
    return 'Out of stock';
  }

  if (overview.lowStock) {
    return 'Low stock';
  }

  return 'Stock available';
}
