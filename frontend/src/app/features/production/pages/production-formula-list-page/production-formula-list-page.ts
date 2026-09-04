import { DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { RouterLink } from '@angular/router';
import { Subject, catchError, forkJoin, map, of, startWith, switchMap, tap } from 'rxjs';

import { mapHttpError } from '../../../../core/http/map-http-error';
import { UiError } from '../../../../core/http/ui-error';
import { EmptyState } from '../../../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../../shared/ui/loading-state/loading-state';
import { InventoryItemDto } from '../../../catalog/data-access/inventory-item.dto';
import { InventoryItemApiService } from '../../../catalog/data-access/inventory-item-api.service';
import { inventoryItemUnitLabel } from '../../../catalog/inventory-item-display';
import { ProductionFormulaDto } from '../../data-access/production-formula.dto';
import { ProductionFormulaApiService } from '../../data-access/production-formula-api.service';

type FormulaListState =
  | { readonly kind: 'loading' }
  | { readonly kind: 'loaded'; readonly formulas: readonly FormulaListItem[] }
  | { readonly kind: 'error'; readonly error: UiError };

interface FormulaListItem {
  readonly formula: ProductionFormulaDto;
  readonly outputItem: InventoryItemDto;
}

@Component({
  selector: 'app-production-formula-list-page',
  imports: [
    DecimalPipe,
    EmptyState,
    ErrorState,
    LoadingState,
    MatButtonModule,
    MatCardModule,
    RouterLink,
  ],
  templateUrl: './production-formula-list-page.html',
  styleUrl: './production-formula-list-page.scss',
})
export class ProductionFormulaListPage {
  private readonly formulaApi = inject(ProductionFormulaApiService);
  private readonly inventoryItemApi = inject(InventoryItemApiService);
  private readonly requests = new Subject<void>();

  protected readonly state = signal<FormulaListState>({ kind: 'loading' });
  protected readonly unitLabel = inventoryItemUnitLabel;

  constructor() {
    this.requests
      .pipe(
        startWith(undefined),
        tap(() => this.state.set({ kind: 'loading' })),
        switchMap(() =>
          this.formulaApi.list().pipe(
            switchMap((formulas) =>
              formulas.length === 0
                ? of([] as readonly FormulaListItem[])
                : forkJoin(
                    formulas.map((formula) =>
                      this.inventoryItemApi.getById(formula.outputInventoryItemId).pipe(
                        map((outputItem): FormulaListItem => ({ formula, outputItem })),
                      ),
                    ),
                  ),
            ),
            map((formulas): FormulaListState => ({ kind: 'loaded', formulas })),
            catchError((error: unknown) =>
              of<FormulaListState>({ kind: 'error', error: mapHttpError(error) }),
            ),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((state) => this.state.set(state));
  }

  protected retry(): void {
    this.requests.next();
  }
}
