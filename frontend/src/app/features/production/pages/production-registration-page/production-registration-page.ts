import { DecimalPipe } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
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
import { MatRadioModule } from '@angular/material/radio';
import { MatSelectModule } from '@angular/material/select';
import { RouterLink } from '@angular/router';
import {
  Observable,
  Subject,
  catchError,
  distinctUntilChanged,
  finalize,
  forkJoin,
  map,
  of,
  startWith,
  switchMap,
  tap,
} from 'rxjs';

import { mapHttpError } from '../../../../core/http/map-http-error';
import { hasUnhandledDetails, localizeFieldError } from '../../../../core/http/localize-ui-error';
import { formatLocalDate } from '../../../../core/i18n/local-date';
import { UiError } from '../../../../core/http/ui-error';
import { EmptyState } from '../../../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../../shared/ui/loading-state/loading-state';
import { InventoryItemDto } from '../../../catalog/data-access/inventory-item.dto';
import { InventoryItemApiService } from '../../../catalog/data-access/inventory-item-api.service';
import { inventoryItemUnitLabel } from '../../../catalog/inventory-item-display';
import {
  BatchInventoryEntryDto,
  BatchOperationalStatus,
  InventoryItemOverviewDto,
} from '../../../inventory/data-access/inventory-operations.dto';
import { InventoryItemOperationsApiService } from '../../../inventory/data-access/inventory-item-operations-api.service';
import {
  ProductionExecutionDto,
  ProductionLotCodeMode,
  RegisterProductionRequest,
} from '../../data-access/production-execution.dto';
import { ProductionExecutionApiService } from '../../data-access/production-execution-api.service';
import {
  ProductionFormulaDto,
  ProductionFormulaIngredientDto,
} from '../../data-access/production-formula.dto';
import { ProductionFormulaApiService } from '../../data-access/production-formula-api.service';

const DECIMAL_PATTERN = /^\d+(?:\.\d{1,6})?$/;
const INLINE_ERROR_FIELDS: readonly string[] = [
  'formulaId',
  'outputQuantity',
  'sourceAllocations',
  'productionDate',
  'outputReceivedAt',
  'outputExpiresAt',
  'lotCodeMode',
  'manualLotCode',
];

type AllocationRowForm = FormGroup<{
  batchId: FormControl<string>;
  quantity: FormControl<string>;
}>;

type IngredientAllocationForm = FormGroup<{
  inventoryItemId: FormControl<string>;
  allocations: FormArray<AllocationRowForm>;
}>;

interface FormulaOption {
  readonly formula: ProductionFormulaDto;
  readonly outputItem: InventoryItemDto;
}

interface IngredientContext {
  readonly ingredient: ProductionFormulaIngredientDto;
  readonly item: InventoryItemDto;
  readonly batches: readonly BatchInventoryEntryDto[];
}

interface FormulaContext {
  readonly formula: ProductionFormulaDto;
  readonly outputItem: InventoryItemDto;
  readonly ingredients: readonly IngredientContext[];
}

interface ReviewAllocation {
  readonly inventoryItem: InventoryItemDto;
  readonly batch: BatchInventoryEntryDto;
  readonly quantity: number;
}

interface ProductionReview {
  readonly request: RegisterProductionRequest;
  readonly context: FormulaContext;
  readonly allocations: readonly ReviewAllocation[];
}

type FormulaLoadState =
  | { readonly kind: 'loading' }
  | { readonly kind: 'empty' }
  | { readonly kind: 'ready'; readonly options: readonly FormulaOption[] }
  | { readonly kind: 'error'; readonly error: UiError };

type FormulaContextState =
  | { readonly kind: 'idle' }
  | { readonly kind: 'loading' }
  | { readonly kind: 'ready'; readonly context: FormulaContext }
  | { readonly kind: 'error'; readonly error: UiError };

type RefreshState =
  | { readonly kind: 'idle' }
  | { readonly kind: 'loading' }
  | { readonly kind: 'ready'; readonly overviews: readonly InventoryItemOverviewDto[] }
  | { readonly kind: 'error'; readonly error: UiError };

@Component({
  selector: 'app-production-registration-page',
  imports: [
    DecimalPipe,
    EmptyState,
    ErrorState,
    LoadingState,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatRadioModule,
    MatSelectModule,
    ReactiveFormsModule,
    RouterLink,
  ],
  templateUrl: './production-registration-page.html',
  styleUrl: './production-registration-page.scss',
})
export class ProductionRegistrationPage {
  private readonly formulaApi = inject(ProductionFormulaApiService);
  private readonly executionApi = inject(ProductionExecutionApiService);
  private readonly inventoryItemApi = inject(InventoryItemApiService);
  private readonly inventoryOperationsApi = inject(InventoryItemOperationsApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly formulaReloads = new Subject<void>();
  private readonly contextRequests = new Subject<string>();
  private readonly formulaOptions = signal<readonly FormulaOption[]>([]);
  private readonly completedContext = signal<FormulaContext | null>(null);

  readonly registrationForm = new FormGroup({
    formulaId: new FormControl('', {
      nonNullable: true,
      validators: Validators.required,
    }),
    outputQuantity: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, positiveDecimal],
    }),
    productionDate: new FormControl('', {
      nonNullable: true,
      validators: Validators.required,
    }),
    outputReceivedAt: new FormControl('', {
      nonNullable: true,
      validators: Validators.required,
    }),
    outputExpiresAt: new FormControl('', { nonNullable: true }),
    lotCodeMode: new FormControl<ProductionLotCodeMode>('GENERATED', { nonNullable: true }),
    manualLotCode: new FormControl('', { nonNullable: true }),
    allocationGroups: new FormArray<IngredientAllocationForm>([]),
  });

  protected readonly allocationGroups = this.registrationForm.controls.allocationGroups;
  protected readonly formulaLoadState = signal<FormulaLoadState>({ kind: 'loading' });
  protected readonly contextState = signal<FormulaContextState>({ kind: 'idle' });
  protected readonly lotCodeMode = signal<ProductionLotCodeMode>('GENERATED');
  protected readonly allocationError = signal<string | null>(null);
  protected readonly submissionError = signal<UiError | null>(null);
  protected readonly isSubmitting = signal(false);
  protected readonly review = signal<ProductionReview | null>(null);
  protected readonly productionResult = signal<ProductionExecutionDto | null>(null);
  protected readonly refreshState = signal<RefreshState>({ kind: 'idle' });
  protected readonly unitLabel = inventoryItemUnitLabel;
  protected readonly formatLocalDate = formatLocalDate;
  protected readonly globalSubmissionError = computed(() => {
    const error = this.submissionError();
    if (error === null || error.details === undefined) {
      return error;
    }

    return hasUnhandledDetails(error, INLINE_ERROR_FIELDS) ? error : null;
  });

  constructor() {
    this.contextRequests
      .pipe(
        tap(() => {
          this.contextState.set({ kind: 'loading' });
          this.allocationGroups.clear();
        }),
        switchMap((formulaId) => this.loadFormulaContext(formulaId)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((state) => {
        this.contextState.set(state);
        if (state.kind === 'ready') {
          this.populateAllocationGroups(state.context);
        }
      });

    this.registrationForm.controls.formulaId.valueChanges
      .pipe(distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe((formulaId) => {
        this.review.set(null);
        this.submissionError.set(null);
        this.allocationError.set(null);
        this.allocationGroups.clear();

        if (formulaId.length === 0) {
          this.contextState.set({ kind: 'idle' });
          return;
        }

        this.contextRequests.next(formulaId);
      });

    this.registrationForm.controls.lotCodeMode.valueChanges
      .pipe(
        startWith(this.registrationForm.controls.lotCodeMode.value),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((mode) => {
        this.lotCodeMode.set(mode);
        const manualLotCode = this.registrationForm.controls.manualLotCode;
        manualLotCode.setValidators(mode === 'MANUAL' ? [manualLotCodeValidator] : []);
        manualLotCode.updateValueAndValidity({ emitEvent: false });
        this.submissionError.set(null);
      });

    this.formulaReloads
      .pipe(
        startWith(undefined),
        tap(() => {
          this.formulaLoadState.set({ kind: 'loading' });
          this.formulaOptions.set([]);
          this.registrationForm.controls.formulaId.setValue('');
        }),
        switchMap(() =>
          this.formulaApi.list().pipe(
            switchMap((formulas) => this.resolveFormulaOptions(formulas)),
            map((options): FormulaLoadState =>
              options.length === 0 ? { kind: 'empty' } : { kind: 'ready', options },
            ),
            catchError((error: unknown) =>
              of<FormulaLoadState>({ kind: 'error', error: mapHttpError(error) }),
            ),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((state) => {
        this.formulaLoadState.set(state);
        this.formulaOptions.set(state.kind === 'ready' ? state.options : []);
      });
  }

  protected retryFormulas(): void {
    this.formulaReloads.next();
  }

  protected retryContext(): void {
    const formulaId = this.registrationForm.controls.formulaId.value;
    if (formulaId.length > 0) {
      this.contextRequests.next(formulaId);
    }
  }

  protected addAllocation(ingredientIndex: number): void {
    if (this.isSubmitting() || this.review() !== null) {
      return;
    }

    this.allocationGroups.at(ingredientIndex).controls.allocations.push(createAllocationRow());
    this.allocationError.set(null);
    this.submissionError.set(null);
  }

  protected removeAllocation(ingredientIndex: number, allocationIndex: number): void {
    const allocations = this.allocationGroups.at(ingredientIndex).controls.allocations;
    if (allocations.length === 1 || this.isSubmitting() || this.review() !== null) {
      return;
    }

    allocations.removeAt(allocationIndex);
    this.allocationError.set(null);
    this.submissionError.set(null);
  }

  protected prepareReview(event: SubmitEvent): void {
    event.preventDefault();
    if (this.isSubmitting() || this.productionResult() !== null) {
      return;
    }

    this.registrationForm.markAllAsTouched();
    this.submissionError.set(null);
    this.allocationError.set(null);

    const currentContext = this.contextState();
    if (currentContext.kind !== 'ready' || this.registrationForm.invalid) {
      return;
    }

    const request = this.toRequest();
    const duplicateBatchId = findDuplicateBatchId(request.sourceAllocations);
    if (duplicateBatchId !== null) {
      this.allocationError.set('Cada lote concreto pode aparecer apenas uma vez na mesma produção.');
      return;
    }

    const allocations = this.resolveReviewAllocations(currentContext.context, request);
    if (allocations === null) {
      this.allocationError.set('Revise os lotes selecionados antes de continuar.');
      return;
    }

    this.review.set({ request, context: currentContext.context, allocations });
  }

  protected editReview(): void {
    if (!this.isSubmitting() && this.productionResult() === null) {
      this.review.set(null);
      this.submissionError.set(null);
    }
  }

  protected confirmProduction(): void {
    const currentReview = this.review();
    if (
      currentReview === null ||
      this.isSubmitting() ||
      this.productionResult() !== null
    ) {
      return;
    }

    this.isSubmitting.set(true);
    this.submissionError.set(null);

    this.executionApi
      .register(currentReview.request)
      .pipe(
        finalize(() => this.isSubmitting.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (result) => {
          this.productionResult.set(result);
          this.completedContext.set(currentReview.context);
          this.refreshInventory(result);
        },
        error: (error: unknown) => {
          this.submissionError.set(mapHttpError(error));
          this.review.set(null);
        },
      });
  }

  protected retryRefresh(): void {
    const result = this.productionResult();
    if (result !== null && this.refreshState().kind !== 'loading') {
      this.refreshInventory(result);
    }
  }

  protected backendFieldError(field: string): string | undefined {
    return localizeFieldError(this.submissionError(), field);
  }

  protected ingredientAt(context: FormulaContext, index: number): IngredientContext {
    return context.ingredients[index]!;
  }

  protected selectedBatch(
    batches: readonly BatchInventoryEntryDto[],
    batchId: string,
  ): BatchInventoryEntryDto | null {
    return batches.find((batch) => batch.batchId === batchId) ?? null;
  }

  protected batchStatusLabel(status: BatchOperationalStatus): string {
    switch (status) {
      case 'AVAILABLE':
        return 'Disponível';
      case 'EXPIRED':
        return 'Expirado';
      case 'ZERO_BALANCE':
        return 'Sem saldo';
    }
  }

  protected itemName(inventoryItemId: string): string {
    const context = this.completedContext();
    if (context === null) {
      return inventoryItemId;
    }
    if (context.outputItem.id === inventoryItemId) {
      return context.outputItem.name;
    }

    return (
      context.ingredients.find((ingredient) => ingredient.item.id === inventoryItemId)?.item.name ??
      inventoryItemId
    );
  }

  protected sourceBatchLabel(batchId: string): string {
    const context = this.completedContext();
    if (context === null) {
      return batchId;
    }

    for (const ingredient of context.ingredients) {
      const batch = ingredient.batches.find((candidate) => candidate.batchId === batchId);
      if (batch !== undefined) {
        return batch.lotCode ?? batch.batchId;
      }
    }
    return batchId;
  }

  private resolveFormulaOptions(
    formulas: readonly ProductionFormulaDto[],
  ): Observable<readonly FormulaOption[]> {
    if (formulas.length === 0) {
      return of([]);
    }

    return forkJoin(
      formulas.map((formula) =>
        this.inventoryItemApi.getById(formula.outputInventoryItemId).pipe(
          map((outputItem): FormulaOption => ({ formula, outputItem })),
        ),
      ),
    );
  }

  private loadFormulaContext(formulaId: string): Observable<FormulaContextState> {
    const option = this.formulaOptions().find((candidate) => candidate.formula.id === formulaId);
    if (option === undefined) {
      return of({
        kind: 'error',
        error: {
          kind: 'not-found',
          code: 'PRODUCTION_FORMULA_NOT_FOUND',
          message: 'Production formula was not found.',
        },
      });
    }

    const loads = option.formula.ingredients.map((ingredient) =>
      forkJoin({
        item: this.inventoryItemApi.getById(ingredient.inventoryItemId),
        batches: this.inventoryOperationsApi.getBatches(ingredient.inventoryItemId),
      }).pipe(
        map(
          ({ item, batches }): IngredientContext => ({
            ingredient,
            item,
            batches: batches.batches,
          }),
        ),
      ),
    );

    const ingredients = loads.length === 0 ? of([] as readonly IngredientContext[]) : forkJoin(loads);
    return ingredients.pipe(
      map(
        (resolvedIngredients): FormulaContextState => ({
          kind: 'ready',
          context: {
            formula: option.formula,
            outputItem: option.outputItem,
            ingredients: resolvedIngredients,
          },
        }),
      ),
      catchError((error: unknown) =>
        of<FormulaContextState>({ kind: 'error', error: mapHttpError(error) }),
      ),
    );
  }

  private populateAllocationGroups(context: FormulaContext): void {
    this.allocationGroups.clear();
    context.ingredients.forEach((ingredient) => {
      this.allocationGroups.push(createIngredientAllocationForm(ingredient.item.id));
    });
  }

  private toRequest(): RegisterProductionRequest {
    const value = this.registrationForm.getRawValue();
    return {
      formulaId: value.formulaId,
      outputQuantity: Number(value.outputQuantity.trim()),
      sourceAllocations: value.allocationGroups.flatMap((group) =>
        group.allocations.map((allocation) => ({
          batchId: allocation.batchId,
          quantity: Number(allocation.quantity.trim()),
        })),
      ),
      productionDate: value.productionDate,
      outputReceivedAt: value.outputReceivedAt,
      outputExpiresAt: normalizeOptional(value.outputExpiresAt),
      lotCodeMode: value.lotCodeMode,
      manualLotCode:
        value.lotCodeMode === 'MANUAL' ? normalizeOptional(value.manualLotCode) : null,
    };
  }

  private resolveReviewAllocations(
    context: FormulaContext,
    request: RegisterProductionRequest,
  ): readonly ReviewAllocation[] | null {
    const result: ReviewAllocation[] = [];
    for (const allocation of request.sourceAllocations) {
      const ingredient = context.ingredients.find((candidate) =>
        candidate.batches.some((batch) => batch.batchId === allocation.batchId),
      );
      const batch = ingredient?.batches.find(
        (candidate) => candidate.batchId === allocation.batchId,
      );
      if (ingredient === undefined || batch === undefined) {
        return null;
      }
      result.push({ inventoryItem: ingredient.item, batch, quantity: allocation.quantity });
    }
    return result;
  }

  private refreshInventory(result: ProductionExecutionDto): void {
    const inventoryItemIds = [
      ...new Set([
        result.outputInventoryItemId,
        ...result.consumptions.map((consumption) => consumption.sourceInventoryItemId),
      ]),
    ];

    this.refreshState.set({ kind: 'loading' });
    forkJoin(
      inventoryItemIds.map((inventoryItemId) =>
        this.inventoryOperationsApi.getOverview(inventoryItemId),
      ),
    )
      .pipe(
        map(
          (overviews): RefreshState => ({
            kind: 'ready',
            overviews,
          }),
        ),
        catchError((error: unknown) =>
          of<RefreshState>({ kind: 'error', error: mapHttpError(error) }),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((state) => this.refreshState.set(state));
  }
}

function createIngredientAllocationForm(inventoryItemId: string): IngredientAllocationForm {
  return new FormGroup({
    inventoryItemId: new FormControl(inventoryItemId, { nonNullable: true }),
    allocations: new FormArray<AllocationRowForm>([createAllocationRow()]),
  });
}

function createAllocationRow(): AllocationRowForm {
  return new FormGroup({
    batchId: new FormControl('', {
      nonNullable: true,
      validators: Validators.required,
    }),
    quantity: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, positiveDecimal],
    }),
  });
}

function positiveDecimal(control: AbstractControl<string>): ValidationErrors | null {
  const normalized = control.value.trim();
  if (control.value.length > 0 && normalized.length === 0) {
    return { decimal: true };
  }
  if (normalized.length === 0) {
    return null;
  }
  if (!DECIMAL_PATTERN.test(normalized)) {
    return { decimal: true };
  }

  const [integerPart] = normalized.split('.');
  const significantIntegerDigits = integerPart.replace(/^0+/, '').length;
  if (significantIntegerDigits > 13 || Number(normalized) <= 0) {
    return { decimal: true };
  }
  return null;
}

function manualLotCodeValidator(control: AbstractControl<string>): ValidationErrors | null {
  const normalized = control.value.trim();
  if (normalized.length === 0) {
    return { required: true };
  }
  return normalized.length > 255 ? { maxlength: true } : null;
}

function normalizeOptional(value: string): string | null {
  const normalized = value.trim();
  return normalized.length === 0 ? null : normalized;
}

function findDuplicateBatchId(
  allocations: readonly { readonly batchId: string }[],
): string | null {
  const seen = new Set<string>();
  for (const allocation of allocations) {
    if (seen.has(allocation.batchId)) {
      return allocation.batchId;
    }
    seen.add(allocation.batchId);
  }
  return null;
}
