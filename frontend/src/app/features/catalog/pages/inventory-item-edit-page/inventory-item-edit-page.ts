import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormField, form, maxLength, required, validate } from '@angular/forms/signals';
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
import { InventoryItemDto, UpdateInventoryItemRequest } from '../../data-access/inventory-item.dto';
import { InventoryItemApiService } from '../../data-access/inventory-item-api.service';
import { inventoryItemCategoryLabel, inventoryItemUnitLabel, productGenderLabel } from '../../inventory-item-display';

interface EditModel {
  readonly name: string;
  readonly description: string;
  readonly active: boolean;
  readonly essenceReference: string;
  readonly productionTypeCode: string;
}

type EditField = keyof EditModel;

type EditState =
  | { readonly kind: 'loading' }
  | { readonly kind: 'loaded'; readonly item: InventoryItemDto }
  | { readonly kind: 'error'; readonly error: UiError };

const EMPTY_EDIT: EditModel = {
  name: '',
  description: '',
  active: true,
  essenceReference: '',
  productionTypeCode: '',
};

const INLINE_ERROR_FIELDS: readonly EditField[] = [
  'name',
  'description',
  'active',
  'essenceReference',
  'productionTypeCode',
];

@Component({
  selector: 'app-inventory-item-edit-page',
  imports: [
    ErrorState,
    FormField,
    LoadingState,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSlideToggleModule,
    RouterLink,
  ],
  templateUrl: './inventory-item-edit-page.html',
  styleUrl: './inventory-item-edit-page.scss',
})
export class InventoryItemEditPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly inventoryItemApi = inject(InventoryItemApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly retries = new Subject<void>();

  readonly editModel = signal<EditModel>({ ...EMPTY_EDIT });
  protected readonly editForm = form(this.editModel, (item) => {
    required(item.name, { message: 'Nome é obrigatório.' });
    maxLength(item.name, 255, { message: 'Nome deve ter no máximo 255 caracteres.' });
    validate(item.name, ({ value }) =>
      value().length > 0 && value().trim().length === 0
        ? { kind: 'blank', message: 'Nome é obrigatório.' }
        : undefined,
    );
    maxLength(item.essenceReference, 3, { message: 'Referência da essência deve ter 3 dígitos.' });
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

  protected readonly state = signal<EditState>({ kind: 'loading' });
  protected readonly isSubmitting = signal(false);
  protected readonly submissionError = signal<UiError | null>(null);
  protected readonly categoryLabel = inventoryItemCategoryLabel;
  protected readonly unitLabel = inventoryItemUnitLabel;
  protected readonly genderLabel = productGenderLabel;
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
        map((params) => params.get('inventoryItemId') ?? ''),
        distinctUntilChanged(),
        switchMap((inventoryItemId) =>
          this.retries.pipe(
            startWith(undefined),
            tap(() => this.state.set({ kind: 'loading' })),
            switchMap(() =>
              this.inventoryItemApi.getById(inventoryItemId).pipe(
                map((item): EditState => ({ kind: 'loaded', item })),
                catchError((error: unknown) =>
                  of<EditState>({ kind: 'error', error: mapHttpError(error) }),
                ),
              ),
            ),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((state) => {
        this.state.set(state);
        if (state.kind === 'loaded') {
          this.editModel.set({
            name: state.item.name,
            description: state.item.description ?? '',
            active: state.item.active,
            essenceReference: state.item.essenceReference ?? '',
            productionTypeCode: state.item.productionTypeCode ?? '',
          });
        }
      });
  }

  protected retry(): void {
    this.retries.next();
  }

  protected canAssignEssenceReference(item: InventoryItemDto): boolean {
    return item.category === 'ESSENCE' || item.category === 'FINISHED_PRODUCT';
  }

  protected submit(event: SubmitEvent): void {
    event.preventDefault();
    if (this.isSubmitting() || this.state().kind !== 'loaded') {
      return;
    }

    this.editForm().markAsTouched();
    if (this.editForm().invalid()) {
      this.editForm.name().focusBoundControl();
      return;
    }

    const model = this.editModel();
    const item = this.state();
    if (item.kind !== 'loaded') {
      return;
    }
    const request: UpdateInventoryItemRequest = {
      name: model.name,
      description: normalizeOptionalText(model.description),
      active: model.active,
      essenceReference: normalizeOptionalText(model.essenceReference),
      productionTypeCode: normalizeOptionalText(model.productionTypeCode),
    };

    this.isSubmitting.set(true);
    this.submissionError.set(null);
    this.inventoryItemApi
      .update(item.item.id, request)
      .pipe(finalize(() => this.isSubmitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => void this.router.navigate(['/catalog', updated.id]),
        error: (error: unknown) => this.submissionError.set(mapHttpError(error)),
      });
  }

  protected backendFieldError(field: EditField): string | undefined {
    return localizeFieldError(this.submissionError(), field);
  }
}

function normalizeOptionalText(value: string): string | null {
  const normalized = value.trim();
  return normalized ? normalized : null;
}

function optionalValueMatches(value: string, pattern: RegExp): boolean {
  return value.trim() === '' || pattern.test(value);
}
