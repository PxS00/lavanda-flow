import { DecimalPipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormField, form, required, validate } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { RouterLink } from '@angular/router';
import { catchError, map, of, Subject, switchMap, tap } from 'rxjs';

import { mapHttpError } from '../../../../core/http/map-http-error';
import { formatLocalDate } from '../../../../core/i18n/local-date';
import { UiError } from '../../../../core/http/ui-error';
import {
  ExpirationAlertEntryDto,
  ExpirationAlertsDto,
  LowStockAlertsDto,
} from '../../data-access/inventory-alert.dto';
import { InventoryAlertApiService } from '../../data-access/inventory-alert-api.service';
import { EmptyState } from '../../../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../../shared/ui/loading-state/loading-state';

interface ExpirationWindowModel {
  readonly windowDays: string;
}

type AlertState<T> =
  | { readonly kind: 'loading' }
  | { readonly kind: 'loaded'; readonly data: T }
  | { readonly kind: 'error'; readonly error: UiError };

const NON_NEGATIVE_INTEGER = /^\d+$/;

@Component({
  selector: 'app-inventory-alerts-page',
  imports: [
    DecimalPipe,
    EmptyState,
    ErrorState,
    FormField,
    LoadingState,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    RouterLink,
  ],
  templateUrl: './inventory-alerts-page.html',
  styleUrl: './inventory-alerts-page.scss',
})
export class InventoryAlertsPage {
  private readonly alertsApi = inject(InventoryAlertApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly lowStockRequests = new Subject<void>();
  private readonly expirationRequests = new Subject<number | undefined>();
  private readonly expirationWindowQuery = signal<number | undefined>(undefined);

  readonly expirationWindowModel = signal<ExpirationWindowModel>({ windowDays: '' });
  protected readonly expirationWindowForm = form(this.expirationWindowModel, (window) => {
    required(window.windowDays, { message: 'Informe a janela de validade.' });
    validate(window.windowDays, ({ value }) => {
      const rawValue = value();
      const normalized = rawValue.trim();
      if (rawValue.length > 0 && normalized.length === 0) {
        return { kind: 'blank', message: 'Informe a janela de validade.' };
      }

      if (normalized.length === 0 || NON_NEGATIVE_INTEGER.test(normalized)) {
        return undefined;
      }

      return { kind: 'non-negative-integer', message: 'Use um número inteiro igual ou maior que zero.' };
    });
  });
  protected readonly lowStockState = signal<AlertState<LowStockAlertsDto>>({ kind: 'loading' });
  protected readonly expirationState = signal<AlertState<ExpirationAlertsDto>>({ kind: 'loading' });
  protected readonly formatLocalDate = formatLocalDate;
  protected readonly expirationStatusLabel = expirationStatusLabel;

  constructor() {
    this.bindLowStockRequests();
    this.bindExpirationRequests();
    this.lowStockRequests.next();
    this.expirationRequests.next(undefined);
  }

  protected refreshLowStock(): void {
    this.lowStockRequests.next();
  }

  protected refreshExpiration(): void {
    this.expirationRequests.next(this.expirationWindowQuery());
  }

  protected applyExpirationWindow(event: SubmitEvent): void {
    event.preventDefault();
    this.expirationWindowForm().markAsTouched();
    if (this.expirationWindowForm().invalid()) {
      this.expirationWindowForm.windowDays().focusBoundControl();
      return;
    }

    const windowDays = Number(this.expirationWindowModel().windowDays.trim());
    this.expirationWindowQuery.set(windowDays);
    this.expirationRequests.next(windowDays);
  }

  private bindLowStockRequests(): void {
    this.lowStockRequests
      .pipe(
        tap(() => this.lowStockState.set({ kind: 'loading' })),
        switchMap(() =>
          this.alertsApi.getLowStockAlerts().pipe(
            map((data): AlertState<LowStockAlertsDto> => ({ kind: 'loaded', data })),
            catchError((error: unknown) =>
              of<AlertState<LowStockAlertsDto>>({ kind: 'error', error: mapHttpError(error) }),
            ),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((state) => this.lowStockState.set(state));
  }

  private bindExpirationRequests(): void {
    this.expirationRequests
      .pipe(
        tap(() => this.expirationState.set({ kind: 'loading' })),
        switchMap((windowDays) =>
          this.alertsApi.getExpirationAlerts(windowDays).pipe(
            map((data): AlertState<ExpirationAlertsDto> => ({ kind: 'loaded', data })),
            catchError((error: unknown) =>
              of<AlertState<ExpirationAlertsDto>>({ kind: 'error', error: mapHttpError(error) }),
            ),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((state) => {
        this.expirationState.set(state);
        if (state.kind === 'loaded') {
          this.expirationWindowModel.set({ windowDays: String(state.data.windowDays) });
        }
      });
  }
}

function expirationStatusLabel(status: ExpirationAlertEntryDto['status']): string {
  return status === 'EXPIRED' ? 'Vencido' : 'Próximo do vencimento';
}
