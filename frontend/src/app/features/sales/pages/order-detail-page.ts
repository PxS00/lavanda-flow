import { DatePipe } from '@angular/common';
import { Component, DestroyRef, ElementRef, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  Subject,
  catchError,
  finalize,
  distinctUntilChanged,
  map,
  of,
  startWith,
  switchMap,
  tap,
} from 'rxjs';
import { mapHttpError } from '../../../core/http/map-http-error';
import { UiError } from '../../../core/http/ui-error';
import { formatDecimalString } from '../../../core/i18n/decimal-string';
import { ErrorState } from '../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../shared/ui/loading-state/loading-state';
import { inventoryItemUnitLabel } from '../../catalog/inventory-item-display';
import { OrderApiService } from '../data-access/order-api.service';
import { OrderDto } from '../data-access/order.dto';

type DetailState =
  { kind: 'loading' } | { kind: 'loaded'; order: OrderDto } | { kind: 'error'; error: UiError };
@Component({
  selector: 'app-order-detail-page',
  imports: [DatePipe, RouterLink, MatButtonModule, MatCardModule, ErrorState, LoadingState],
  templateUrl: './order-detail-page.html',
  styleUrl: './sales-pages.scss',
})
export class OrderDetailPage {
  private readonly api = inject(OrderApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly route = inject(ActivatedRoute);
  private readonly retries = new Subject<void>();
  private readonly statusHeading = viewChild<ElementRef<HTMLHeadingElement>>('statusHeading');
  protected readonly state = signal<DetailState>({ kind: 'loading' });
  protected readonly pending = signal(false);
  protected readonly confirmationError = signal<UiError | null>(null);
  protected readonly formatDecimal = formatDecimalString;
  protected readonly unitLabel = inventoryItemUnitLabel;
  constructor() {
    this.route.paramMap
      .pipe(
        map((params) => params.get('orderId') ?? ''),
        distinctUntilChanged(),
        switchMap((id) =>
          this.retries.pipe(
            startWith(undefined),
            tap(() => {
              this.state.set({ kind: 'loading' });
              this.confirmationError.set(null);
            }),
            switchMap(() =>
              this.api.getById(id).pipe(
                map((order): DetailState => ({ kind: 'loaded', order })),
                catchError((error: unknown) =>
                  of<DetailState>({ kind: 'error', error: mapHttpError(error) }),
                ),
              ),
            ),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((state) => this.state.set(state));
  }
  protected confirm(): void {
    const current = this.state();
    if (this.pending() || current.kind !== 'loaded' || current.order.status !== 'DRAFT') return;
    this.pending.set(true);
    this.confirmationError.set(null);
    const id = current.order.id;
    this.api
      .confirm(id)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.pending.set(false)),
      )
      .subscribe({
        next: (order) => {
          const latest = this.state();
          if (latest.kind === 'loaded' && latest.order.id === id) {
            this.state.set({ kind: 'loaded', order });
            this.statusHeading()?.nativeElement.focus();
          }
        },
        error: (error: unknown) => {
          const latest = this.state();
          if (latest.kind !== 'loaded' || latest.order.id !== id) return;
          this.confirmationError.set(mapHttpError(error));
        },
      });
  }
  protected retry(): void {
    this.retries.next();
  }
}
