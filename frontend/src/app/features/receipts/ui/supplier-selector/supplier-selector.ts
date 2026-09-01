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
import { SupplierDto } from '../../../suppliers/data-access/supplier.dto';
import { SupplierApiService } from '../../../suppliers/data-access/supplier-api.service';

const SEARCH_PAGE_SIZE = 10;

type SearchState =
  | { readonly kind: 'loading' }
  | { readonly kind: 'loaded'; readonly suppliers: readonly SupplierDto[] }
  | { readonly kind: 'error'; readonly error: UiError };

@Component({
  selector: 'app-supplier-selector',
  imports: [ErrorState, LoadingState, MatButtonModule, MatFormFieldModule, MatInputModule],
  templateUrl: './supplier-selector.html',
  styleUrl: './supplier-selector.scss',
})
export class SupplierSelector {
  private readonly api = inject(SupplierApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly searches = new Subject<string>();

  readonly selected = input<SupplierDto | null>(null);
  readonly disabled = input(false);
  readonly selectionChange = output<SupplierDto | null>();

  protected readonly query = signal('');
  protected readonly state = signal<SearchState>({ kind: 'loading' });

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
            map((page): SearchState => ({ kind: 'loaded', suppliers: page.content })),
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

  protected choose(supplier: SupplierDto): void {
    this.query.set(supplier.name);
    this.selectionChange.emit(supplier);
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
