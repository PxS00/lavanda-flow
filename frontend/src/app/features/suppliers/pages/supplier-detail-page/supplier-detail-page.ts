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
import { SupplierDto } from '../../data-access/supplier.dto';
import { SupplierApiService } from '../../data-access/supplier-api.service';

type SupplierDetailState =
  | { readonly kind: 'loading' }
  | { readonly kind: 'loaded'; readonly supplier: SupplierDto }
  | { readonly kind: 'error'; readonly error: UiError };

@Component({
  selector: 'app-supplier-detail-page',
  imports: [ErrorState, LoadingState, MatButtonModule, MatCardModule, RouterLink],
  templateUrl: './supplier-detail-page.html',
  styleUrl: './supplier-detail-page.scss',
})
export class SupplierDetailPage {
  private readonly route = inject(ActivatedRoute);
  private readonly supplierApi = inject(SupplierApiService);
  private readonly retries = new Subject<void>();

  protected readonly state = signal<SupplierDetailState>({ kind: 'loading' });

  constructor() {
    this.route.paramMap
      .pipe(
        map((params) => params.get('supplierId') ?? ''),
        distinctUntilChanged(),
        switchMap((supplierId) =>
          this.retries.pipe(
            startWith(undefined),
            tap(() => this.state.set({ kind: 'loading' })),
            switchMap(() =>
              this.supplierApi.getById(supplierId).pipe(
                map((supplier): SupplierDetailState => ({ kind: 'loaded', supplier })),
                catchError((error: unknown) =>
                  of<SupplierDetailState>({ kind: 'error', error: mapHttpError(error) }),
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
