import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormArray,
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
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  Observable,
  Subject,
  catchError,
  distinctUntilChanged,
  forkJoin,
  map,
  of,
  startWith,
  switchMap,
  tap,
} from 'rxjs';

import { mapHttpError } from '../../../../core/http/map-http-error';
import { hasUnhandledDetails, localizeFieldError } from '../../../../core/http/localize-ui-error';
import { UiError } from '../../../../core/http/ui-error';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../../shared/ui/loading-state/loading-state';
import { InventoryItemDto } from '../../../catalog/data-access/inventory-item.dto';
import { InventoryItemApiService } from '../../../catalog/data-access/inventory-item-api.service';
import { InventoryItemSelector } from '../../../catalog/ui/inventory-item-selector/inventory-item-selector';
import {
  ProductionFormulaDto,
  UpsertProductionFormulaRequest,
} from '../../data-access/production-formula.dto';
import { ProductionFormulaApiService } from '../../data-access/production-formula-api.service';

const DECIMAL_PATTERN = /^\d+(?:\.\d{1,6})?$/;
const INLINE_ERROR_FIELDS = ['outputInventoryItemId', 'outputQuantity', 'ingredients'];

type IngredientForm = FormGroup<{
  inventoryItem: FormControl<InventoryItemDto | null>;
  quantity: FormControl<string>;
}>;

type FormulaFormLoadState =
  | { readonly kind: 'loading' }
  | { readonly kind: 'ready' }
  | { readonly kind: 'error'; readonly error: UiError };

@Component({
  selector: 'app-production-formula-form-page',
  imports: [
    ErrorState,
    LoadingState,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    InventoryItemSelector,
    ReactiveFormsModule,
    RouterLink,
  ],
  templateUrl: './production-formula-form-page.html',
  styleUrl: './production-formula-form-page.scss',
})
export class ProductionFormulaFormPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly formulaApi = inject(ProductionFormulaApiService);
  private readonly inventoryItemApi = inject(InventoryItemApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly reloads = new Subject<void>();
  private readonly formulaId = signal<string | null>(null);

  readonly formulaForm = new FormGroup({
    outputInventoryItem: new FormControl<InventoryItemDto | null>(null, Validators.required),
    outputQuantity: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, positiveDecimal],
    }),
    ingredients: new FormArray<IngredientForm>([createIngredientForm()]),
  });

  protected readonly state = signal<FormulaFormLoadState>({ kind: 'loading' });
  protected readonly isSubmitting = signal(false);
  protected readonly submissionError = signal<UiError | null>(null);
  protected readonly successMessage = signal<string | null>(null);
  protected readonly isEditing = computed(() => this.formulaId() !== null);
  protected readonly ingredients = this.formulaForm.controls.ingredients;
  protected readonly globalSubmissionError = computed(() => {
    const error = this.submissionError();
    if (error === null || error.details === undefined) {
      return error;
    }

    return hasUnhandledDetails(error, INLINE_ERROR_FIELDS) ? error : null;
  });

  constructor() {
    this.route.paramMap
      .pipe(
        map((params) => params.get('formulaId')),
        distinctUntilChanged(),
        switchMap((formulaId) =>
          this.reloads.pipe(
            startWith(undefined),
            tap(() => {
              this.formulaId.set(formulaId);
              this.state.set({ kind: 'loading' });
              this.submissionError.set(null);
              this.successMessage.set(null);
            }),
            switchMap(() => this.load(formulaId)),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((state) => this.state.set(state));
  }

  protected addIngredient(): void {
    this.ingredients.push(createIngredientForm());
    this.submissionError.set(null);
    this.successMessage.set(null);
  }

  protected removeIngredient(index: number): void {
    if (this.ingredients.length === 1 || this.isSubmitting()) {
      return;
    }

    this.ingredients.removeAt(index);
    this.submissionError.set(null);
    this.successMessage.set(null);
  }

  protected submit(event: SubmitEvent): void {
    event.preventDefault();
    if (this.isSubmitting()) {
      return;
    }

    this.formulaForm.markAllAsTouched();
    if (this.formulaForm.invalid) {
      return;
    }

    const request = this.toRequest();
    if (request === null) {
      return;
    }

    const formulaId = this.formulaId();
    this.isSubmitting.set(true);
    this.submissionError.set(null);
    this.successMessage.set(null);

    const write = formulaId === null
      ? this.formulaApi.create(request)
      : this.formulaApi.update(formulaId, request);

    write.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (formula) => {
        this.isSubmitting.set(false);
        if (formulaId === null) {
          void this.router.navigate(['/production/formulas', formula.id]);
          return;
        }

        this.formulaForm.markAsPristine();
        this.successMessage.set('Fórmula atualizada com sucesso.');
      },
      error: (error: unknown) => {
        this.isSubmitting.set(false);
        this.submissionError.set(mapHttpError(error));
      },
    });
  }

  protected retry(): void {
    this.reloads.next();
  }

  protected backendFieldError(field: string): string | undefined {
    return localizeFieldError(this.submissionError(), field);
  }

  private load(formulaId: string | null) {
    const formula: Observable<ProductionFormulaDto | null> = formulaId === null
      ? of(null)
      : this.formulaApi.getById(formulaId);

    return formula.pipe(
      switchMap((formula) =>
        formula === null
          ? of({ formula, items: [] as readonly InventoryItemDto[] })
          : forkJoin(
              [...new Set([formula.outputInventoryItemId, ...formula.ingredients.map((ingredient) => ingredient.inventoryItemId)])]
                .map((inventoryItemId) => this.inventoryItemApi.getById(inventoryItemId)),
            ).pipe(map((items) => ({ formula, items }))),
      ),
      map(({ formula, items }): FormulaFormLoadState => {
        this.populateForm(formula, items);
        return { kind: 'ready' };
      }),
      catchError((error: unknown) =>
        of<FormulaFormLoadState>({ kind: 'error', error: mapHttpError(error) }),
      ),
    );
  }

  private populateForm(formula: ProductionFormulaDto | null, items: readonly InventoryItemDto[] = []): void {
    this.ingredients.clear();
    if (formula === null) {
      this.formulaForm.reset({ outputInventoryItem: null, outputQuantity: '' });
      this.ingredients.push(createIngredientForm());
      return;
    }

    const itemById = new Map(items.map((item) => [item.id, item]));
    this.formulaForm.controls.outputInventoryItem.setValue(itemById.get(formula.outputInventoryItemId) ?? null);
    this.formulaForm.controls.outputQuantity.setValue(String(formula.outputQuantity));
    formula.ingredients.forEach((ingredient) => {
      this.ingredients.push(createIngredientForm(itemById.get(ingredient.inventoryItemId) ?? null, String(ingredient.quantity)));
    });
    this.formulaForm.markAsPristine();
  }

  private toRequest(): UpsertProductionFormulaRequest | null {
    const value = this.formulaForm.getRawValue();
    if (value.outputInventoryItem === null || value.ingredients.some((ingredient) => ingredient.inventoryItem === null)) {
      return null;
    }

    return {
      outputInventoryItemId: value.outputInventoryItem.id,
      outputQuantity: Number(value.outputQuantity.trim()),
      ingredients: value.ingredients.map((ingredient) => ({
        inventoryItemId: ingredient.inventoryItem!.id,
        quantity: Number(ingredient.quantity.trim()),
      })),
    };
  }
}

function createIngredientForm(inventoryItem: InventoryItemDto | null = null, quantity = ''): IngredientForm {
  return new FormGroup({
    inventoryItem: new FormControl<InventoryItemDto | null>(inventoryItem, Validators.required),
    quantity: new FormControl(quantity, {
      nonNullable: true,
      validators: [Validators.required, positiveDecimal],
    }),
  });
}

function positiveDecimal(control: AbstractControl<string>): ValidationErrors | null {
  const normalized = control.value.trim();
  if (normalized.length === 0) {
    return null;
  }

  if (!DECIMAL_PATTERN.test(normalized)) {
    return { decimal: true };
  }

  const [integerPart] = normalized.split('.');
  if (integerPart.replace(/^0+/, '').length > 13 || Number(normalized) <= 0) {
    return { decimal: true };
  }

  return null;
}
