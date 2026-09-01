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
}

type RegistrationField = keyof RegistrationModel;

const INLINE_ERROR_FIELDS: readonly RegistrationField[] = [
  'name',
  'description',
  'category',
  'unitOfMeasure',
];

const EMPTY_REGISTRATION: RegistrationModel = {
  name: '',
  description: '',
  category: null,
  unitOfMeasure: null,
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
    required(item.name, { message: 'Name is required.' });
    maxLength(item.name, 255, { message: 'Name must be 255 characters or fewer.' });
    validate(item.name, ({ value }) =>
      value().length > 0 && value().trim().length === 0
        ? { kind: 'blank', message: 'Name is required.' }
        : undefined,
    );
    required(item.category, { message: 'Category is required.' });
    required(item.unitOfMeasure, { message: 'Unit of measure is required.' });
  });
  protected readonly categoryOptions = INVENTORY_ITEM_CATEGORY_OPTIONS;
  protected readonly unitOptions = INVENTORY_ITEM_UNIT_OPTIONS;
  protected readonly isSubmitting = signal(false);
  protected readonly submissionError = signal<UiError | null>(null);
  protected readonly globalSubmissionError = computed(() => {
    const error = this.submissionError();
    if (error === null || error.fieldErrors === undefined) {
      return error;
    }

    const errorFields = Object.keys(error.fieldErrors);
    if (errorFields.length === 0) {
      return error;
    }

    const unmappedFieldErrors = Object.fromEntries(
      Object.entries(error.fieldErrors).filter(
        ([field]) => !INLINE_ERROR_FIELDS.includes(field as RegistrationField),
      ),
    );

    return Object.keys(unmappedFieldErrors).length > 0
      ? { ...error, fieldErrors: unmappedFieldErrors }
      : null;
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
    return this.submissionError()?.fieldErrors?.[field];
  }
}

function normalizeDescription(description: string): string | null {
  return description.trim() ? description : null;
}
