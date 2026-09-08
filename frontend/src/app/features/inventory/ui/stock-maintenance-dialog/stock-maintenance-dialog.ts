import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatRadioModule } from '@angular/material/radio';
import { finalize, Observable } from 'rxjs';

import { hasUnhandledDetails, localizeFieldError } from '../../../../core/http/localize-ui-error';
import { mapHttpError } from '../../../../core/http/map-http-error';
import { UiError } from '../../../../core/http/ui-error';
import { formatDecimalString } from '../../../../core/i18n/decimal-string';
import { formatLocalDate } from '../../../../core/i18n/local-date';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import {
  BatchInventoryEntryDto,
  BatchOperationalStatus,
} from '../../data-access/inventory-operations.dto';
import { StockMaintenanceApiService } from '../../data-access/stock-maintenance-api.service';
import {
  StockMaintenanceMovementDto,
  StockMaintenanceRequest,
} from '../../data-access/stock-maintenance.dto';

type StockMaintenanceOperation = 'ADJUSTMENT' | 'LOSS' | 'EXPIRED_DISPOSAL';

interface PendingMaintenance extends StockMaintenanceRequest {
  readonly operation: StockMaintenanceOperation;
}

export type StockMaintenanceDialogData = BatchInventoryEntryDto;

const ADJUSTMENT_PATTERN = /^-?\d{1,13}(?:\.\d{1,6})?$/;
const POSITIVE_PATTERN = /^\d{1,13}(?:\.\d{1,6})?$/;
const INLINE_FIELDS = ['quantity', 'reason'];

@Component({
  selector: 'app-stock-maintenance-dialog',
  imports: [
    ErrorState,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatRadioModule,
    ReactiveFormsModule,
  ],
  templateUrl: './stock-maintenance-dialog.html',
  styleUrl: './stock-maintenance-dialog.scss',
})
export class StockMaintenanceDialog {
  private readonly maintenanceApi = inject(StockMaintenanceApiService);
  private readonly dialogRef = inject(
    MatDialogRef<StockMaintenanceDialog, StockMaintenanceMovementDto>,
  );
  private readonly destroyRef = inject(DestroyRef);

  readonly batch = inject<StockMaintenanceDialogData>(MAT_DIALOG_DATA);
  readonly maintenanceForm = new FormGroup({
    operation: new FormControl<StockMaintenanceOperation>('ADJUSTMENT', { nonNullable: true }),
    quantity: new FormControl('', { nonNullable: true, validators: [quantityValidator] }),
    reason: new FormControl('', {
      nonNullable: true,
      validators: [reasonValidator, Validators.maxLength(255)],
    }),
  });
  protected readonly isConfirming = signal(false);
  protected readonly isSubmitting = signal(false);
  protected readonly submissionError = signal<UiError | null>(null);
  protected readonly pendingMaintenance = signal<PendingMaintenance | null>(null);
  protected readonly formatDecimal = formatDecimalString;
  protected readonly formatLocalDate = formatLocalDate;
  protected readonly batchStatusLabel = batchStatusLabel;
  protected readonly globalSubmissionError = computed(() => {
    const error = this.submissionError();
    if (error === null || error.details === undefined) {
      return error;
    }

    return hasUnhandledDetails(error, INLINE_FIELDS) ? error : null;
  });

  constructor() {
    this.maintenanceForm.controls.operation.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.maintenanceForm.controls.quantity.updateValueAndValidity());
  }

  protected submit(): void {
    if (this.isSubmitting()) {
      return;
    }

    this.maintenanceForm.markAllAsTouched();
    if (this.maintenanceForm.invalid) {
      return;
    }

    const pending: PendingMaintenance = {
      operation: this.maintenanceForm.controls.operation.value,
      quantity: this.maintenanceForm.controls.quantity.value.trim(),
      reason: this.maintenanceForm.controls.reason.value.trim(),
    };
    this.pendingMaintenance.set(pending);
    this.submissionError.set(null);

    if (isDestructive(pending)) {
      this.isConfirming.set(true);
      return;
    }

    this.register(pending);
  }

  protected cancelConfirmation(): void {
    if (this.isSubmitting()) {
      return;
    }

    this.submissionError.set(null);
    this.isConfirming.set(false);
  }

  protected confirm(): void {
    if (!this.isConfirming() || this.isSubmitting()) {
      return;
    }

    const pending = this.pendingMaintenance();
    if (pending === null) {
      this.isConfirming.set(false);
      return;
    }

    this.register(pending);
  }

  protected close(): void {
    if (!this.isSubmitting()) {
      this.dialogRef.close();
    }
  }

  protected fieldError(field: 'quantity' | 'reason'): string | undefined {
    return localizeFieldError(this.submissionError(), field);
  }

  protected controlError(field: 'quantity' | 'reason'): string | null {
    const control = this.maintenanceForm.controls[field];
    if (!control.touched || control.valid) {
      return null;
    }

    if (field === 'reason') {
      return control.hasError('maxlength')
        ? 'Motivo deve ter no máximo 255 caracteres.'
        : 'Motivo é obrigatório.';
    }

    return this.maintenanceForm.controls.operation.value === 'ADJUSTMENT'
      ? 'Use um ajuste diferente de zero, com até 13 dígitos inteiros e 6 casas decimais.'
      : 'Use uma quantidade positiva, com até 13 dígitos inteiros e 6 casas decimais.';
  }

  private register(pending: PendingMaintenance): void {
    if (this.isSubmitting()) {
      return;
    }

    this.isSubmitting.set(true);
    this.submissionError.set(null);

    this.requestFor(pending)
      .pipe(
        finalize(() => this.isSubmitting.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (movement) => this.dialogRef.close(movement),
        error: (error: unknown) => {
          this.submissionError.set(mapHttpError(error));
          this.isConfirming.set(false);
        },
      });
  }

  private requestFor(pending: PendingMaintenance): Observable<StockMaintenanceMovementDto> {
    const request: StockMaintenanceRequest = {
      quantity: pending.quantity,
      reason: pending.reason,
    };

    switch (pending.operation) {
      case 'ADJUSTMENT':
        return this.maintenanceApi.adjust(this.batch.batchId, request);
      case 'LOSS':
        return this.maintenanceApi.registerLoss(this.batch.batchId, request);
      case 'EXPIRED_DISPOSAL':
        return this.maintenanceApi.registerExpiredDisposal(this.batch.batchId, request);
    }
  }
}

function quantityValidator(control: AbstractControl<string>): ValidationErrors | null {
  const normalized = control.value.trim();
  if (normalized.length === 0) {
    return { required: true };
  }

  const operation = control.parent?.get('operation')?.value;
  const pattern = operation === 'ADJUSTMENT' ? ADJUSTMENT_PATTERN : POSITIVE_PATTERN;
  return pattern.test(normalized) && /[1-9]/.test(normalized) ? null : { quantity: true };
}

function reasonValidator(control: AbstractControl<string>): ValidationErrors | null {
  return control.value.trim().length === 0 ? { required: true } : null;
}

function isDestructive(maintenance: PendingMaintenance): boolean {
  return maintenance.operation !== 'ADJUSTMENT' || maintenance.quantity.startsWith('-');
}

function batchStatusLabel(status: BatchOperationalStatus): string {
  return { AVAILABLE: 'Disponível', EXPIRED: 'Vencido', ZERO_BALANCE: 'Saldo zerado' }[status];
}
