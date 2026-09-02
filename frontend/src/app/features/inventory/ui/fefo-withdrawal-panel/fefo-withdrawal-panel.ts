import { DecimalPipe } from '@angular/common';
import { Component, DestroyRef, computed, inject, input, linkedSignal, output } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormField, form, maxLength, required, validate } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

import { mapHttpError } from '../../../../core/http/map-http-error';
import { hasUnhandledDetails, localizeFieldError } from '../../../../core/http/localize-ui-error';
import { UiError } from '../../../../core/http/ui-error';
import { formatDecimalString } from '../../../../core/i18n/decimal-string';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { inventoryItemUnitLabel } from '../../../catalog/inventory-item-display';
import { InventoryUnitOfMeasure } from '../../data-access/inventory-operations.dto';
import { RegisterFefoWithdrawalDto, RegisterFefoWithdrawalRequest } from '../../data-access/fefo-withdrawal.dto';
import { FefoWithdrawalApiService } from '../../data-access/fefo-withdrawal-api.service';

const DECIMAL_PATTERN = /^\d+(?:\.\d{1,6})?$/;
const INLINE_FIELDS = ['quantity', 'reason'];

interface WithdrawalFormModel {
  readonly quantity: string;
  readonly reason: string;
}

interface PendingWithdrawal {
  readonly quantity: number;
  readonly reason: string | null;
}

type WithdrawalState = 'editing' | 'confirmation' | 'submitting' | 'success';

const EMPTY_FORM: WithdrawalFormModel = { quantity: '', reason: '' };

@Component({
  selector: 'app-fefo-withdrawal-panel',
  imports: [
    DecimalPipe,
    ErrorState,
    FormField,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  templateUrl: './fefo-withdrawal-panel.html',
  styleUrl: './fefo-withdrawal-panel.scss',
})
export class FefoWithdrawalPanel {
  private readonly withdrawalApi = inject(FefoWithdrawalApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly inventoryItemId = input.required<string>();
  readonly itemName = input.required<string>();
  readonly unitOfMeasure = input.required<InventoryUnitOfMeasure>();
  readonly availableQuantity = input.required<number>();
  readonly active = input.required<boolean>();
  readonly withdrawalCompleted = output<void>();

  readonly withdrawalModel = linkedSignal(() => {
    this.inventoryItemId();
    return { ...EMPTY_FORM };
  });
  protected readonly withdrawalForm = form(this.withdrawalModel, (withdrawal) => {
    required(withdrawal.quantity, { message: 'Quantidade é obrigatória.' });
    validate(withdrawal.quantity, ({ value }) => validateQuantity(value()));
    maxLength(withdrawal.reason, 255, { message: 'Motivo deve ter no máximo 255 caracteres.' });
  });

  protected readonly state = linkedSignal((): WithdrawalState => {
    this.inventoryItemId();
    return 'editing';
  });
  protected readonly pendingWithdrawal = linkedSignal((): PendingWithdrawal | null => {
    this.inventoryItemId();
    return null;
  });
  protected readonly completedWithdrawal = linkedSignal((): RegisterFefoWithdrawalDto | null => {
    this.inventoryItemId();
    return null;
  });
  protected readonly submissionError = linkedSignal((): UiError | null => {
    this.inventoryItemId();
    return null;
  });
  protected readonly serverConfirmedInactive = linkedSignal(() => {
    this.inventoryItemId();
    return false;
  });
  protected readonly unitLabel = inventoryItemUnitLabel;
  protected readonly effectivelyActive = computed(
    () => this.active() && !this.serverConfirmedInactive(),
  );
  protected readonly globalSubmissionError = computed(() => {
    const error = this.submissionError();
    if (error === null || error.details === undefined || this.state() !== 'editing') {
      return error;
    }

    return hasUnhandledDetails(error, INLINE_FIELDS) ? error : null;
  });
  protected readonly insufficientStockMessage = computed(() => {
    const error = this.submissionError();
    if (error?.code !== 'INSUFFICIENT_ELIGIBLE_STOCK') {
      return null;
    }

    const available = error.details?.['availableQuantity'];
    return available === undefined
      ? 'Nenhuma saída parcial foi registrada. Revise a quantidade e tente novamente quando houver estoque elegível.'
      : `Nenhuma saída parcial foi registrada. Há ${formatDecimalString(available)} ${this.unitLabel(this.unitOfMeasure())} de estoque elegível.`;
  });

  protected requestConfirmation(event: SubmitEvent): void {
    event.preventDefault();
    if (this.state() !== 'editing' || !this.effectivelyActive()) {
      return;
    }

    this.withdrawalForm().markAsTouched();
    if (this.withdrawalForm().invalid()) {
      this.withdrawalForm.quantity().focusBoundControl();
      return;
    }

    const model = this.withdrawalModel();
    this.pendingWithdrawal.set({
      quantity: Number(model.quantity.trim()),
      reason: normalizeOptional(model.reason),
    });
    this.submissionError.set(null);
    this.state.set('confirmation');
  }

  protected returnToEditing(): void {
    if (this.state() === 'submitting') {
      return;
    }

    this.submissionError.set(null);
    this.state.set('editing');
  }

  protected confirmWithdrawal(): void {
    if (this.state() !== 'confirmation' || !this.effectivelyActive()) {
      return;
    }

    const withdrawal = this.pendingWithdrawal();
    if (withdrawal === null) {
      this.state.set('editing');
      return;
    }

    const inventoryItemId = this.inventoryItemId();
    const request: RegisterFefoWithdrawalRequest = withdrawal;
    this.state.set('submitting');
    this.submissionError.set(null);

    this.withdrawalApi
      .register(inventoryItemId, request)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          if (this.inventoryItemId() !== inventoryItemId) {
            return;
          }

          this.completedWithdrawal.set(response);
          this.state.set('success');
          this.withdrawalCompleted.emit();
        },
        error: (error: unknown) => {
          if (this.inventoryItemId() !== inventoryItemId) {
            return;
          }

          const mappedError = mapHttpError(error);
          this.submissionError.set(mappedError);
          if (mappedError.code === 'INACTIVE_INVENTORY_ITEM') {
            this.serverConfirmedInactive.set(true);
          }
          this.state.set(
            mappedError.kind === 'validation' && hasEditableFieldError(mappedError)
              ? 'editing'
              : 'confirmation',
          );
        },
      });
  }

  protected startAnotherWithdrawal(): void {
    this.withdrawalForm().reset({ ...EMPTY_FORM });
    this.pendingWithdrawal.set(null);
    this.completedWithdrawal.set(null);
    this.submissionError.set(null);
    this.state.set('editing');
  }

  protected backendFieldError(field: 'quantity' | 'reason'): string | undefined {
    return localizeFieldError(this.submissionError(), field);
  }
}

function normalizeOptional(value: string): string | null {
  const normalized = value.trim();
  return normalized.length === 0 ? null : normalized;
}

function validateQuantity(
  rawValue: string,
): { readonly kind: string; readonly message: string } | undefined {
  const normalized = rawValue.trim();
  if (rawValue.length > 0 && normalized.length === 0) {
    return { kind: 'blank', message: 'Quantidade é obrigatória.' };
  }

  if (normalized.length === 0) {
    return undefined;
  }

  if (!DECIMAL_PATTERN.test(normalized)) {
    return { kind: 'decimal', message: 'Use uma quantidade positiva com até 6 casas decimais.' };
  }

  const [integerPart] = normalized.split('.');
  if (integerPart.replace(/^0+/, '').length > 13 || Number(normalized) <= 0) {
    return {
      kind: 'quantity-range',
      message: 'Use uma quantidade positiva com no máximo 13 dígitos inteiros e 6 casas decimais.',
    };
  }

  const quantity = Number(normalized);
  if (!Number.isFinite(quantity) || normalizeDecimal(JSON.stringify(quantity)) !== normalizeDecimal(normalized)) {
    return {
      kind: 'unsafe-number',
      message: 'Esta quantidade não pode ser representada com segurança pelo contrato numérico atual.',
    };
  }

  return undefined;
}

function normalizeDecimal(value: string): string {
  const [rawIntegerPart, rawFractionPart] = value.split('.');
  const integerPart = rawIntegerPart.replace(/^0+(?=\d)/, '');
  const fractionPart = rawFractionPart?.replace(/0+$/, '') ?? '';
  return fractionPart.length > 0 ? `${integerPart}.${fractionPart}` : integerPart;
}

function hasEditableFieldError(error: UiError): boolean {
  return error.details?.['quantity'] !== undefined || error.details?.['reason'] !== undefined;
}
