import { DatePipe } from '@angular/common';
import { Component, DestroyRef, ElementRef, inject, input, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Subject, catchError, finalize, map, of, startWith, switchMap } from 'rxjs';
import { mapHttpError } from '../../../core/http/map-http-error';
import { UiError } from '../../../core/http/ui-error';
import { formatDecimalString } from '../../../core/i18n/decimal-string';
import { ErrorState } from '../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../shared/ui/loading-state/loading-state';
import { inventoryItemUnitLabel } from '../../catalog/inventory-item-display';
import { OrderApiService } from '../data-access/order-api.service';
import {
  OrderDto,
  RegisterSaleStockCorrection,
  SaleStockCorrectionDto,
} from '../data-access/order.dto';
import { normalizeDraftDecimal, validDraftDecimal } from '../draft-preview';

type HistoryState =
  | { kind: 'loading' }
  | { kind: 'error'; error: UiError }
  | { kind: 'loaded'; corrections: readonly SaleStockCorrectionDto[] };

@Component({
  selector: 'app-sale-stock-correction-form',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    ErrorState,
    LoadingState,
  ],
  templateUrl: './sale-stock-correction-form.html',
})
export class SaleStockCorrectionForm {
  readonly sale = input.required<OrderDto>();
  private readonly api = inject(OrderApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly reload = new Subject<void>();
  private readonly formElement = viewChild<ElementRef<HTMLFormElement>>('correctionForm');
  private readonly resultHeading = viewChild<ElementRef<HTMLHeadingElement>>('resultHeading');
  private attempt: { id: string; request: RegisterSaleStockCorrection } | null = null;
  readonly form = new FormGroup({
    allocation: new FormControl('', { nonNullable: true, validators: Validators.required }),
    quantity: new FormControl('', {
      nonNullable: true,
      validators: (c: AbstractControl<string>) =>
        validDraftDecimal(c.value, 13, 6, true) ? null : { decimal: true },
    }),
    reason: new FormControl('', {
      nonNullable: true,
      validators: (c: AbstractControl<string>) =>
        c.value.trim().length > 0 && c.value.trim().length <= 255 ? null : { reason: true },
    }),
    inspected: new FormControl(false, { nonNullable: true, validators: Validators.requiredTrue }),
    accepted: new FormControl(false, { nonNullable: true, validators: Validators.requiredTrue }),
  });
  protected readonly pending = signal(false);
  protected readonly failure = signal<UiError | null>(null);
  protected readonly result = signal<SaleStockCorrectionDto | null>(null);
  protected readonly history = signal<HistoryState>({ kind: 'loading' });
  protected readonly formatDecimal = formatDecimalString;
  protected readonly unitLabel = inventoryItemUnitLabel;

  constructor() {
    toObservable(this.sale)
      .pipe(
        switchMap((sale) =>
          this.reload.pipe(
            startWith(undefined),
            switchMap(() => {
              this.history.set({ kind: 'loading' });
              return this.api.stockCorrections(sale.id).pipe(
                map((corrections): HistoryState => ({ kind: 'loaded', corrections })),
                catchError((error: unknown) =>
                  of<HistoryState>({ kind: 'error', error: mapHttpError(error) }),
                ),
              );
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((history) => this.history.set(history));
  }

  protected submit(event: Event): void {
    event.preventDefault();
    if (this.pending() || this.sale().status !== 'CONFIRMED') return;
    this.form.markAllAsTouched();
    if (!this.attempt) {
      if (this.form.invalid) {
        this.formElement()?.nativeElement.querySelector<HTMLElement>('.ng-invalid')?.focus();
        return;
      }
      const value = this.form.getRawValue();
      const [lineId, batchId] = value.allocation.split('/');
      if (!lineId || !batchId) return;
      let id: string;
      try {
        id = newCorrectionId();
      } catch {
        this.failure.set({
          kind: 'unknown',
          code: 'SALE_CORRECTION_ID_UNAVAILABLE',
          message: 'Unable to generate a secure correction identity.',
        });
        return;
      }
      this.attempt = {
        id,
        request: {
          lineId,
          batchId,
          quantity: normalizeDraftDecimal(value.quantity),
          reason: value.reason.trim(),
          inspected: value.inspected,
          accepted: value.accepted,
        },
      };
    }
    this.pending.set(true);
    this.failure.set(null);
    this.form.disable();
    this.api
      .correctStock(this.sale().id, this.attempt.id, this.attempt.request)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.pending.set(false)),
      )
      .subscribe({
        next: (result) => {
          this.result.set(result);
          this.attempt = null;
          this.form.reset();
          this.form.enable();
          this.reload.next();
          this.resultHeading()?.nativeElement.focus();
        },
        error: (error: unknown) => this.failure.set(mapHttpError(error)),
      });
  }

  protected canRevise(): boolean {
    const error = this.failure();
    return (
      error !== null &&
      (error.kind === 'validation' ||
        error.kind === 'unprocessable' ||
        error.code === 'SALE_CORRECTION_LIMIT_EXCEEDED')
    );
  }
  protected revise(): void {
    if (this.pending() || !this.canRevise()) return;
    // A definitive rejection has no effects; ambiguous failures must retry the original identity/payload.
    this.attempt = null;
    this.failure.set(null);
    this.form.enable();
  }
  protected retryHistory(): void {
    this.reload.next();
  }
}

function newCorrectionId(): string {
  const crypto = globalThis.crypto;
  if (typeof crypto?.randomUUID === 'function') return crypto.randomUUID();
  // randomUUID requires a secure context; getRandomValues also supports the approved LAN HTTP origin.
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6]! & 0x0f) | 0x40;
  bytes[8] = (bytes[8]! & 0x3f) | 0x80;
  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}
