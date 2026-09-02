import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormField, form, maxLength, required, validate } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { mapHttpError } from '../../../../core/http/map-http-error';
import { hasUnhandledDetails, localizeFieldError } from '../../../../core/http/localize-ui-error';
import { UiError } from '../../../../core/http/ui-error';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { RegisterSupplierRequest } from '../../data-access/supplier.dto';
import { SupplierApiService } from '../../data-access/supplier-api.service';

interface SupplierRegistrationModel {
  readonly name: string;
  readonly identifier: string;
  readonly contact: string;
  readonly notes: string;
}

type SupplierRegistrationField = keyof SupplierRegistrationModel;

const INLINE_ERROR_FIELDS: readonly SupplierRegistrationField[] = [
  'name',
  'identifier',
  'contact',
  'notes',
];

const EMPTY_REGISTRATION: SupplierRegistrationModel = {
  name: '',
  identifier: '',
  contact: '',
  notes: '',
};

@Component({
  selector: 'app-supplier-registration-page',
  imports: [ErrorState, FormField, MatButtonModule, MatFormFieldModule, MatInputModule, RouterLink],
  templateUrl: './supplier-registration-page.html',
  styleUrl: './supplier-registration-page.scss',
})
export class SupplierRegistrationPage {
  private readonly supplierApi = inject(SupplierApiService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly registrationModel = signal<SupplierRegistrationModel>({ ...EMPTY_REGISTRATION });
  protected readonly registrationForm = form(this.registrationModel, (supplier) => {
    required(supplier.name, { message: 'Nome é obrigatório.' });
    maxLength(supplier.name, 255, { message: 'Nome deve ter no máximo 255 caracteres.' });
    validate(supplier.name, ({ value }) =>
      value().length > 0 && value().trim().length === 0
        ? { kind: 'blank', message: 'Nome é obrigatório.' }
        : undefined,
    );
    maxLength(supplier.identifier, 255, {
      message: 'Identificador deve ter no máximo 255 caracteres.',
    });
    maxLength(supplier.contact, 255, { message: 'Contato deve ter no máximo 255 caracteres.' });
  });
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
    const request: RegisterSupplierRequest = {
      name: model.name,
      identifier: normalizeOptionalText(model.identifier),
      contact: normalizeOptionalText(model.contact),
      notes: normalizeOptionalText(model.notes),
    };

    this.isSubmitting.set(true);
    this.submissionError.set(null);
    this.supplierApi
      .register(request)
      .pipe(
        finalize(() => this.isSubmitting.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (supplier) => void this.router.navigate(['/suppliers', supplier.id]),
        error: (error: unknown) => this.submissionError.set(mapHttpError(error)),
      });
  }

  protected backendFieldError(field: SupplierRegistrationField): string | undefined {
    return localizeFieldError(this.submissionError(), field);
  }
}

function normalizeOptionalText(value: string): string | null {
  return value.trim() ? value : null;
}
