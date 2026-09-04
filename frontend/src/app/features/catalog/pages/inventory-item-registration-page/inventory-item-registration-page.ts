import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormField, form, maxLength, required, validate } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { mapHttpError } from '../../../../core/http/map-http-error';
import { hasUnhandledDetails, localizeFieldError } from '../../../../core/http/localize-ui-error';
import { UiError } from '../../../../core/http/ui-error';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import {
  InventoryItemCategory,
  InventoryItemUnitOfMeasure,
  RegisterInventoryItemRequest,
} from '../../data-access/inventory-item.dto';
import { InventoryItemApiService } from '../../data-access/inventory-item-api.service';
import {
  INVENTORY_ITEM_CATEGORY_OPTIONS,
  INVENTORY_ITEM_UNIT_OPTIONS,
} from '../../inventory-item-display';

interface RegistrationModel {
  readonly name: string;
  readonly description: string;
  readonly category: InventoryItemCategory | null;
  readonly unitOfMeasure: InventoryItemUnitOfMeasure | null;
  readonly essenceReference: string;
  readonly productionTypeCode: string;
}

type RegistrationField = keyof RegistrationModel;

const INLINE_ERROR_FIELDS: readonly RegistrationField[] = [
  'name',
  'description',
  'category',
  'unitOfMeasure',
  'essenceReference',
  'productionTypeCode',
];

const EMPTY_REGISTRATION: RegistrationModel = {
  name: '',
  description: '',
  category: null,
  unitOfMeasure: null,
  essenceReference: '',
  productionTypeCode: '',
};

@Component({
  selector: 'app-inventory-item-registration-page',
  imports: [
    ErrorState,
    FormField,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    RouterLink,
  ],
  templateUrl: './inventory-item-registration-page.html',
  styleUrl: './inventory-item-registration-page.scss',
})
export class InventoryItemRegistrationPage {
  private readonly inventoryItemApi = inject(InventoryItemApiService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly registrationModel = signal<RegistrationModel>({ ...EMPTY_REGISTRATION });
  protected readonly registrationForm = form(this.registrationModel, (item) => {
    required(item.name, { message: 'Nome é obrigatório.' });
    maxLength(item.name, 255, { message: 'Nome deve ter no máximo 255 caracteres.' });
    validate(item.name, ({ value }) =>
      value().length > 0 && value().trim().length === 0
        ? { kind: 'blank', message: 'Nome é obrigatório.' }
        : undefined,
    );
    required(item.category, { message: 'Categoria é obrigatória.' });
    required(item.unitOfMeasure, { message: 'Unidade de medida é obrigatória.' });
    maxLength(item.essenceReference, 3, {
      message: 'Referência da essência deve ter 3 dígitos.',
    });
    validate(item.essenceReference, ({ value }) =>
      optionalValueMatches(value(), /^(?:00[1-9]|0[1-9][0-9]|[1-9][0-9]{2})$/)
        ? undefined
        : { kind: 'essence-reference', message: 'Use uma referência de 001 a 999.' },
    );
    maxLength(item.productionTypeCode, 3, {
      message: 'Código do tipo de produção deve ter 3 letras.',
    });
    validate(item.productionTypeCode, ({ value }) =>
      optionalValueMatches(value(), /^[A-Z]{3}$/)
        ? undefined
        : { kind: 'production-type-code', message: 'Use 3 letras maiúsculas.' },
    );
  });
  protected readonly categoryOptions = INVENTORY_ITEM_CATEGORY_OPTIONS;
  protected readonly unitOptions = INVENTORY_ITEM_UNIT_OPTIONS;
  protected readonly isSubmitting = signal(false);
  protected readonly submissionError = signal<UiError | null>(null);
  protected readonly globalSubmissionError = computed(() => {
    const error = this.submissionError();
    if (error === null || error.details === undefined) {
      return error;
    }

    const errorFields = Object.keys(error.details);
    if (errorFields.length === 0) {
      return error;
    }

    return hasUnhandledDetails(error, INLINE_ERROR_FIELDS) ? error : null;
  });

  protected submit(event: SubmitEvent): void {
    event.preventDefault();

    if (this.isSubmitting()) {
      return;
    }

    this.registrationForm().markAsTouched();
    if (this.registrationForm().invalid()) {
      this.registrationForm.name().focusBoundControl();
      return;
    }

    const model = this.registrationModel();
    if (model.category === null || model.unitOfMeasure === null) {
      return;
    }

    const request: RegisterInventoryItemRequest = {
      name: model.name,
      description: normalizeDescription(model.description),
      category: model.category,
      unitOfMeasure: model.unitOfMeasure,
      essenceReference: normalizeOptionalText(model.essenceReference),
      productionTypeCode: normalizeOptionalText(model.productionTypeCode),
    };

    this.isSubmitting.set(true);
    this.submissionError.set(null);
    this.inventoryItemApi
      .register(request)
      .pipe(
        finalize(() => this.isSubmitting.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (item) => void this.router.navigate(['/catalog', item.id]),
        error: (error: unknown) => this.submissionError.set(mapHttpError(error)),
      });
  }

  protected backendFieldError(field: RegistrationField): string | undefined {
    return localizeFieldError(this.submissionError(), field);
  }
}

function normalizeDescription(description: string): string | null {
  return normalizeOptionalText(description);
}

function normalizeOptionalText(value: string): string | null {
  const normalized = value.trim();
  return normalized.length > 0 ? normalized : null;
}

function optionalValueMatches(value: string, pattern: RegExp): boolean {
  const normalized = value.trim();
  return normalized.length === 0 || pattern.test(normalized);
}
