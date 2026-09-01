import { Component, DestroyRef, effect, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Subject, catchError, debounceTime, distinctUntilChanged, map, of, startWith, switchMap, tap } from 'rxjs';

import { mapHttpError } from '../../../../core/http/map-http-error';
import { UiError } from '../../../../core/http/ui-error';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../../shared/ui/loading-state/loading-state';
import { InventoryItemDto } from '../../../catalog/data-access/inventory-item.dto';
import { InventoryItemApiService } from '../../../catalog/data-access/inventory-item-api.service';
import { inventoryItemUnitLabel } from '../../../catalog/inventory-item-display';

const SEARCH_PAGE_SIZE = 10;

type SearchState =
  | { readonly kind: 'loading' }
  | { readonly kind: 'loaded'; readonly items: readonly InventoryItemDto[] }
  | { readonly kind: 'error'; readonly error: UiError };

@Component({
  selector: 'app-inventory-item-selector',
  imports: [ErrorState, LoadingState, MatButtonModule, MatFormFieldModule, MatInputModule],
  templateUrl: './inventory-item-selector.html',
  styleUrl: './inventory-item-selector.scss',
})
export class InventoryItemSelector {
  private readonly api = inject(InventoryItemApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly searches = new Subject<string>();

  readonly selected = input<InventoryItemDto | null>(null);
  readonly disabled = input(false);
  readonly selectionChange = output<InventoryItemDto | null>();

  protected readonly query = signal('');
  protected readonly state = signal<SearchState>({ kind: 'loading' });
  protected readonly unitLabel = inventoryItemUnitLabel;

  constructor() {
    effect(() => {
      const selected = this.selected();
      if (selected !== null && this.query() !== selected.name) {
        this.query.set(selected.name);
      }
    });

    this.searches
      .pipe(
        startWith(''),
        debounceTime(180),
        distinctUntilChanged(),
        tap(() => this.state.set({ kind: 'loading' })),
        switchMap((name) =>
          this.api.search({ name, active: true, page: 0, size: SEARCH_PAGE_SIZE }).pipe(
            map((page): SearchState => ({ kind: 'loaded', items: page.content })),
            catchError((error: unknown) =>
              of<SearchState>({ kind: 'error', error: mapHttpError(error) }),
            ),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((state) => this.state.set(state));
  }

  protected search(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.query.set(value);

    const selected = this.selected();
    if (selected !== null && value.trim() !== selected.name) {
      this.selectionChange.emit(null);
    }

    this.searches.next(value);
  }

  protected choose(item: InventoryItemDto): void {
    this.query.set(item.name);
    this.selectionChange.emit(item);
  }

  protected clear(): void {
    this.query.set('');
    this.selectionChange.emit(null);
    this.searches.next('');
  }

  protected retry(): void {
    this.searches.next(this.query());
  }
}
