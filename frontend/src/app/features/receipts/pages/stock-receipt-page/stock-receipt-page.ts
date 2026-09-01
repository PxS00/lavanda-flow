import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormField, form, maxLength, required, validate } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, distinctUntilChanged, finalize, map, of, switchMap } from 'rxjs';

import { mapHttpError } from '../../../../core/http/map-http-error';
import { UiError } from '../../../../core/http/ui-error';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { InventoryItemDto } from '../../../catalog/data-access/inventory-item.dto';
import { InventoryItemApiService } from '../../../catalog/data-access/inventory-item-api.service';
import { inventoryItemUnitLabel } from '../../../catalog/inventory-item-display';
import { SupplierDto } from '../../../suppliers/data-access/supplier.dto';
import { StockReceiptApiService } from '../../data-access/stock-receipt-api.service';
import { RegisterStockReceiptDto, RegisterStockReceiptRequest } from '../../data-access/stock-receipt.dto';
import { InventoryItemSelector } from '../../ui/inventory-item-selector/inventory-item-selector';
import { SupplierSelector } from '../../ui/supplier-selector/supplier-selector';

const DECIMAL_PATTERN = /^\d+(?:\.\d{1,6})?$/;

type ReceiptField = 'lotCode' | 'quantity' | 'receivedAt' | 'expiresAt' | 'reason';
const INLINE_FIELDS: readonly string[] = [
  'inventoryItemId',
  'supplierId',
  'lotCode',
  'quantity',
  'receivedAt',
  'expiresAt',
  'reason',
];

interface ReceiptFormModel {
  readonly lotCode: string;
  readonly quantity: string;
  readonly receivedAt: string;
  readonly expiresAt: string;
  readonly reason: string;
}

const EMPTY_FORM: ReceiptFormModel = {
  lotCode: '',
  quantity: '',
  receivedAt: '',
  expiresAt: '',
  reason: '',
};

type PreselectionResult =
  | { readonly kind: 'none' }
  | { readonly kind: 'item'; readonly item: InventoryItemDto }
  | { readonly kind: 'error'; readonly error: UiError };

@Component({
  selector: 'app-stock-receipt-page',
  imports: [
    ErrorState,
    FormField,
    InventoryItemSelector,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    RouterLink,
    SupplierSelector,
  ],
  templateUrl: './stock-receipt-page.html',
  styleUrl: './stock-receipt-page.scss',
})
export class StockReceiptPage {
  private readonly receiptApi = inject(StockReceiptApiService);
  private readonly inventoryItemApi = inject(InventoryItemApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  readonly receiptModel = signal<ReceiptFormModel>({ ...EMPTY_FORM });
  protected readonly receiptForm = form(this.receiptModel, (receipt) => {
    maxLength(receipt.lotCode, 255, { message: 'Lot code must be 255 characters or fewer.' });
    required(receipt.quantity, { message: 'Quantity is required.' });
    validate(receipt.quantity, ({ value }) => validateQuantity(value()));
    required(receipt.receivedAt, { message: 'Received date is required.' });
    maxLength(receipt.reason, 255, { message: 'Reason must be 255 characters or fewer.' });
  });

  protected readonly selectedItem = signal<InventoryItemDto | null>(null);
  protected readonly selectedSupplier = signal<SupplierDto | null>(null);
  protected readonly selectionError = signal<string | null>(null);
  protected readonly preselectionError = signal<UiError | null>(null);
  protected readonly submissionError = signal<UiError | null>(null);
  protected readonly isSubmitting = signal(false);
  protected readonly createdReceipt = signal<RegisterStockReceiptDto | null>(null);
  protected readonly unitLabel = inventoryItemUnitLabel;
  protected readonly globalSubmissionError = computed(() => {
    const error = this.submissionError();
    if (error === null || error.fieldErrors === undefined) {
      return error;
    }

    const remaining = Object.fromEntries(
      Object.entries(error.fieldErrors).filter(([field]) => !INLINE_FIELDS.includes(field)),
    );

    return Object.keys(remaining).length > 0 ? { ...error, fieldErrors: remaining } : null;
  });

  constructor() {
    this.route.queryParamMap
      .pipe(
        map((params) => params.get('inventoryItemId')),
        distinctUntilChanged(),
        switchMap((inventoryItemId) => {
          if (inventoryItemId === null || inventoryItemId.length === 0) {
            return of<PreselectionResult>({ kind: 'none' });
          }

          return this.inventoryItemApi.getById(inventoryItemId).pipe(
            map((item): PreselectionResult => ({ kind: 'item', item })),
            catchError((error: unknown) =>
              of<PreselectionResult>({ kind: 'error', error: mapHttpError(error) }),
            ),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => {
        this.preselectionError.set(null);
        if (result.kind === 'item') {
          if (result.item.active) {
            this.selectedItem.set(result.item);
            this.selectionError.set(null);
          } else {
            this.selectedItem.set(null);
            this.selectionError.set('This inventory item is inactive and cannot receive stock.');
          }
        } else if (result.kind === 'error') {
          this.preselectionError.set(result.error);
        }
      });
  }

  protected selectItem(item: InventoryItemDto | null): void {
    this.selectedItem.set(item);
    this.selectionError.set(null);
    this.submissionError.set(null);
  }

  protected selectSupplier(supplier: SupplierDto | null): void {
    this.selectedSupplier.set(supplier);
    this.submissionError.set(null);
  }

  protected submit(event: SubmitEvent): void {
    event.preventDefault();
    if (this.isSubmitting()) {
      return;
    }

    this.receiptForm().markAsTouched();
    const item = this.selectedItem();
    if (item === null) {
      this.selectionError.set('Select an active inventory item before registering the receipt.');
    }

    if (this.receiptForm().invalid() || item === null) {
      if (this.receiptForm.quantity().invalid()) {
        this.receiptForm.quantity().focusBoundControl();
      } else if (this.receiptForm.receivedAt().invalid()) {
        this.receiptForm.receivedAt().focusBoundControl();
      }
      return;
    }

    const model = this.receiptModel();
    const supplier = this.selectedSupplier();
    const request: RegisterStockReceiptRequest = {
      inventoryItemId: item.id,
      supplierId: supplier?.id ?? null,
      lotCode: normalizeOptional(model.lotCode),
      quantity: Number(model.quantity.trim()),
      receivedAt: model.receivedAt,
      expiresAt: normalizeOptional(model.expiresAt),
      reason: normalizeOptional(model.reason),
    };

    this.isSubmitting.set(true);
    this.submissionError.set(null);
    this.createdReceipt.set(null);

    this.receiptApi
      .register(request)
      .pipe(
        finalize(() => this.isSubmitting.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (receipt) => this.createdReceipt.set(receipt),
        error: (error: unknown) => this.submissionError.set(mapHttpError(error)),
      });
  }

  protected registerAnother(): void {
    const receivedAt = this.receiptModel().receivedAt;
    this.receiptForm().reset({ ...EMPTY_FORM, receivedAt });
    this.createdReceipt.set(null);
    this.submissionError.set(null);
    this.selectionError.set(null);
  }

  protected backendFieldError(field: ReceiptField | 'inventoryItemId' | 'supplierId'): string | undefined {
    return this.submissionError()?.fieldErrors?.[field];
  }
}

function normalizeOptional(value: string): string | null {
  const normalized = value.trim();
  return normalized.length > 0 ? normalized : null;
}

function validateQuantity(rawValue: string): { readonly kind: string; readonly message: string } | undefined {
  const normalized = rawValue.trim();
  if (rawValue.length > 0 && normalized.length === 0) {
    return { kind: 'blank', message: 'Quantity is required.' };
  }

  if (normalized.length === 0) {
    return undefined;
  }

  if (!DECIMAL_PATTERN.test(normalized)) {
    return { kind: 'decimal', message: 'Use a positive quantity with up to 6 decimal places.' };
  }

  const [integerPart] = normalized.split('.');
  const significantIntegerDigits = integerPart.replace(/^0+/, '').length;
  if (significantIntegerDigits > 13 || Number(normalized) <= 0) {
    return {
      kind: 'quantity-range',
      message: 'Use a positive quantity with at most 13 integer digits and 6 decimal places.',
    };
  }

  return undefined;
}
