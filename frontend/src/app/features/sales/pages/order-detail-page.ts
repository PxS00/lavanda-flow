import { DatePipe } from '@angular/common';
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
  private readonly route = inject(ActivatedRoute);
  private readonly retries = new Subject<void>();
  protected readonly state = signal<DetailState>({ kind: 'loading' });
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
            tap(() => this.state.set({ kind: 'loading' })),
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
  protected retry(): void {
    this.retries.next();
  }
}
