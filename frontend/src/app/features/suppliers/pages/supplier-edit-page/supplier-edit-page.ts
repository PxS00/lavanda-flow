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
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, catchError, distinctUntilChanged, finalize, map, of, startWith, switchMap, tap } from 'rxjs';

import { mapHttpError } from '../../../../core/http/map-http-error';
import { hasUnhandledDetails, localizeFieldError } from '../../../../core/http/localize-ui-error';
import { UiError } from '../../../../core/http/ui-error';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../../shared/ui/loading-state/loading-state';
import { SupplierDto, UpdateSupplierRequest } from '../../data-access/supplier.dto';
import { SupplierApiService } from '../../data-access/supplier-api.service';

type SupplierEditState =
  | { readonly kind: 'loading' }
  | { readonly kind: 'loaded'; readonly supplier: SupplierDto }
  | { readonly kind: 'error'; readonly error: UiError };

type SupplierEditField = 'name' | 'identifier' | 'contact' | 'notes';

const INLINE_ERROR_FIELDS: readonly SupplierEditField[] = ['name', 'identifier', 'contact', 'notes'];

@Component({
  selector: 'app-supplier-edit-page',
  imports: [
    ErrorState,
    LoadingState,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSlideToggleModule,
    ReactiveFormsModule,
    RouterLink,
  ],
  templateUrl: './supplier-edit-page.html',
  styleUrl: './supplier-edit-page.scss',
})
export class SupplierEditPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly supplierApi = inject(SupplierApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly retries = new Subject<void>();

  readonly editForm = new FormGroup({
    name: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, nonBlank, Validators.maxLength(255)],
    }),
    identifier: new FormControl('', { nonNullable: true, validators: Validators.maxLength(255) }),
    contact: new FormControl('', { nonNullable: true, validators: Validators.maxLength(255) }),
    notes: new FormControl('', { nonNullable: true }),
    active: new FormControl(true, { nonNullable: true }),
  });
  protected readonly state = signal<SupplierEditState>({ kind: 'loading' });
  protected readonly isSubmitting = signal(false);
  protected readonly submissionError = signal<UiError | null>(null);
  protected readonly globalSubmissionError = computed(() => {
    const error = this.submissionError();
    if (error === null || error.details === undefined || Object.keys(error.details).length === 0) {
      return error;
    }

    return hasUnhandledDetails(error, INLINE_ERROR_FIELDS) ? error : null;
  });

  constructor() {
    this.route.paramMap
      .pipe(
        map((params) => params.get('supplierId') ?? ''),
        distinctUntilChanged(),
        switchMap((supplierId) =>
          this.retries.pipe(
            startWith(undefined),
            tap(() => {
              this.state.set({ kind: 'loading' });
              this.submissionError.set(null);
            }),
            switchMap(() =>
              this.supplierApi.getById(supplierId).pipe(
                map((supplier): SupplierEditState => ({ kind: 'loaded', supplier })),
                catchError((error: unknown) =>
                  of<SupplierEditState>({ kind: 'error', error: mapHttpError(error) }),
                ),
              ),
            ),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((state) => {
        this.state.set(state);
        if (state.kind === 'loaded') {
          this.editForm.reset({
            name: state.supplier.name,
            identifier: state.supplier.identifier ?? '',
            contact: state.supplier.contact ?? '',
            notes: state.supplier.notes ?? '',
            active: state.supplier.active,
          });
          this.editForm.markAsPristine();
        }
      });
  }

  protected retry(): void {
    this.retries.next();
  }

  protected submit(event: SubmitEvent): void {
    event.preventDefault();
    const currentState = this.state();
    if (this.isSubmitting() || currentState.kind !== 'loaded') {
      return;
    }

    this.editForm.markAllAsTouched();
    if (this.editForm.invalid) {
      return;
    }

    const value = this.editForm.getRawValue();
    const request: UpdateSupplierRequest = {
      name: value.name,
      identifier: normalizeOptionalText(value.identifier),
      contact: normalizeOptionalText(value.contact),
      notes: normalizeOptionalText(value.notes),
      active: value.active,
    };

    this.isSubmitting.set(true);
    this.submissionError.set(null);
    this.supplierApi
      .update(currentState.supplier.id, request)
      .pipe(finalize(() => this.isSubmitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (supplier) => void this.router.navigate(['/suppliers', supplier.id]),
        error: (error: unknown) => this.submissionError.set(mapHttpError(error)),
      });
  }

  protected backendFieldError(field: SupplierEditField): string | undefined {
    return localizeFieldError(this.submissionError(), field);
  }
}

function nonBlank(control: AbstractControl<string>): ValidationErrors | null {
  return control.value.trim() === '' ? { blank: true } : null;
}

function normalizeOptionalText(value: string): string | null {
  const normalized = value.trim();
  return normalized ? normalized : null;
}
