import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  Subject,
  catchError,
  distinctUntilChanged,
  finalize,
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
import { CustomerDto } from '../../data-access/customer.dto';
import { CustomerApiService } from '../../data-access/customer-api.service';

type CustomerDetailState =
  | { readonly kind: 'loading' }
  | { readonly kind: 'loaded'; readonly customer: CustomerDto }
  | { readonly kind: 'error'; readonly error: UiError };

@Component({
  selector: 'app-customer-detail-page',
  imports: [ErrorState, LoadingState, MatButtonModule, MatCardModule, RouterLink],
  templateUrl: './customer-detail-page.html',
  styleUrl: './customer-detail-page.scss',
})
export class CustomerDetailPage {
  private readonly route = inject(ActivatedRoute);
  private readonly customerApi = inject(CustomerApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly retries = new Subject<void>();

  protected readonly state = signal<CustomerDetailState>({ kind: 'loading' });

  protected readonly isSubmitting = signal(false);
  protected readonly submissionError = signal<UiError | null>(null);

  constructor() {
    this.route.paramMap
      .pipe(
        map((params) => params.get('customerId') ?? ''),
        distinctUntilChanged(),
        switchMap((customerId) =>
          this.retries.pipe(
            startWith(undefined),
            tap(() => {
              this.state.set({ kind: 'loading' });
              this.submissionError.set(null);
            }),
            switchMap(() =>
              this.customerApi.getById(customerId).pipe(
                map((customer): CustomerDetailState => ({ kind: 'loaded', customer })),
                catchError((error: unknown) =>
                  of<CustomerDetailState>({ kind: 'error', error: mapHttpError(error) }),
                ),
              ),
            ),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((state) => this.state.set(state));
  }

  protected changeActiveState(): void {
    const state = this.state();
    if (state.kind !== 'loaded' || this.isSubmitting()) {
      return;
    }
    const customer = state.customer;
    this.isSubmitting.set(true);
    this.submissionError.set(null);
    const request = customer.active
      ? this.customerApi.deactivate(customer.id)
      : this.customerApi.activate(customer.id);
    request
      .pipe(
        finalize(() => this.isSubmitting.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (updated) => {
          const current = this.state();
          if (current.kind === 'loaded' && current.customer.id === updated.id) {
            this.state.set({ kind: 'loaded', customer: updated });
          }
        },
        error: (error: unknown) => {
          const current = this.state();
          if (current.kind === 'loaded' && current.customer.id === customer.id) {
            this.submissionError.set(mapHttpError(error));
          }
        },
      });
  }

  protected retry(): void {
    this.retries.next();
  }
}
