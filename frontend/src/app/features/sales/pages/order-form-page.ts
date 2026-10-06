import {
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  afterNextRender,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormArray,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  Subject,
  catchError,
  distinctUntilChanged,
  finalize,
  map,
  of,
  startWith,
  switchMap,
  tap,
} from 'rxjs';
import { localizeFieldError } from '../../../core/http/localize-ui-error';
import { mapHttpError } from '../../../core/http/map-http-error';
import { UiError } from '../../../core/http/ui-error';
import { formatDecimalString } from '../../../core/i18n/decimal-string';
import { ErrorState } from '../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../shared/ui/loading-state/loading-state';
import { InventoryItemDto } from '../../catalog/data-access/inventory-item.dto';
import { inventoryItemUnitLabel } from '../../catalog/inventory-item-display';
import { CustomerDto } from '../../customers/data-access/customer.dto';
import { OrderApiService } from '../data-access/order-api.service';
import { OrderDto, OrderLineDto, SaveDraftRequest } from '../data-access/order.dto';
import { draftTotalPreview, normalizeDraftDecimal, validDraftDecimal } from '../draft-preview';
import { CustomerPicker } from '../ui/customer-picker';
import { SellableItemPicker } from '../ui/sellable-item-picker';

function lineForm(line: OrderLineDto) {
  return new FormGroup({
    id: new FormControl<string | null>(line.id || null),
    itemId: new FormControl(line.itemId, { nonNullable: true, validators: Validators.required }),
    quantity: new FormControl(line.quantity, {
      nonNullable: true,
      validators: (c: AbstractControl<string>) =>
        validDraftDecimal(c.value, 13, 6, true) ? null : { decimal: true },
    }),
    unitPrice: new FormControl(line.unitPrice, {
      nonNullable: true,
      validators: (c: AbstractControl<string>) =>
        validDraftDecimal(c.value, 15, 4, false) ? null : { decimal: true },
    }),
  });
}
type FormState =
  | { kind: 'loading' }
  | { kind: 'loaded'; order: OrderDto | null }
  | { kind: 'error'; error: UiError };

@Component({
  selector: 'app-order-form-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    CustomerPicker,
    SellableItemPicker,
    ErrorState,
    LoadingState,
  ],
  templateUrl: './order-form-page.html',
  styleUrl: './sales-pages.scss',
})
export class OrderFormPage {
  private readonly api = inject(OrderApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly retries = new Subject<void>();
  private readonly formElement = viewChild<ElementRef<HTMLFormElement>>('draftForm');
  readonly form = new FormGroup({
    customerId: new FormControl<string | null>(null),
    lines: new FormArray<ReturnType<typeof lineForm>>([], Validators.minLength(1)),
  });
  protected readonly state = signal<FormState>({ kind: 'loading' });
  protected readonly pending = signal(false);
  protected readonly failure = signal<UiError | null>(null);
  protected readonly lineError = signal<string | null>(null);
  protected readonly customerName = signal<string | null>(null);
  protected readonly labels = signal<Readonly<Record<string, { name: string; unit: string }>>>({});
  protected readonly preview = signal<string | null>(null);
  protected readonly formatDecimal = formatDecimalString;

  constructor() {
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.updatePreview());
    this.route.paramMap
      .pipe(
        map((params) => params.get('orderId')),
        distinctUntilChanged(),
        switchMap((id) =>
          this.retries.pipe(
            startWith(undefined),
            tap(() => {
              this.state.set({ kind: 'loading' });
              this.failure.set(null);
              this.lineError.set(null);
            }),
            switchMap(() =>
              (id === null ? of<OrderDto | null>(null) : this.api.getById(id)).pipe(
                map((order): FormState => ({ kind: 'loaded', order })),
                catchError((error: unknown) =>
                  of<FormState>({ kind: 'error', error: mapHttpError(error) }),
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
          this.form.controls.customerId.setValue(state.order?.customerId ?? null);
          this.customerName.set(state.order?.customerName ?? null);
          this.form.controls.lines.clear();
          this.labels.set({});
          for (const line of state.order?.lines ?? []) {
            this.form.controls.lines.push(lineForm(line));
            this.labels.update((labels) => ({
              ...labels,
              [line.itemId]: {
                name: line.itemName ?? line.itemId,
                unit: line.unitOfMeasure
                  ? inventoryItemUnitLabel(line.unitOfMeasure)
                  : 'Unidade indisponível',
              },
            }));
          }
          this.updatePreview();
        }
      });
  }
  protected chooseCustomer(customer: CustomerDto): void {
    if (this.pending()) return;
    this.form.controls.customerId.setValue(customer.id);
    this.customerName.set(customer.name);
  }
  protected clearCustomer(): void {
    if (this.pending()) return;
    this.form.controls.customerId.setValue(null);
    this.customerName.set(null);
  }
  protected addItem(item: InventoryItemDto): void {
    if (this.pending()) return;
    if (this.form.controls.lines.controls.some((line) => line.controls.itemId.value === item.id)) {
      this.lineError.set('Este produto já está no pedido. Edite a linha existente.');
      return;
    }
    this.lineError.set(null);
    this.labels.update((labels) => ({
      ...labels,
      [item.id]: { name: item.name, unit: inventoryItemUnitLabel(item.unitOfMeasure) },
    }));
    this.form.controls.lines.push(
      lineForm({
        id: '',
        itemId: item.id,
        itemName: item.name,
        unitOfMeasure: item.unitOfMeasure,
        quantity: '',
        unitPrice: '',
        amount: '',
      }),
    );
    this.focusLine(this.form.controls.lines.length - 1);
  }
  protected removeLine(index: number): void {
    if (this.pending()) return;
    this.form.controls.lines.removeAt(index);
    this.lineError.set(null);
    this.focusLine(Math.min(index, this.form.controls.lines.length - 1));
  }
  protected backendFieldError(field: string): string | undefined {
    return localizeFieldError(this.failure(), field);
  }
  private focusLine(index: number): void {
    afterNextRender(
      () => {
        const form = this.formElement()?.nativeElement;
        const input =
          form?.querySelectorAll<HTMLInputElement>('input[formControlName="quantity"]')[index] ??
          form?.querySelector<HTMLInputElement>('app-product-picker input');
        input?.focus();
      },
      { injector: this.injector },
    );
  }
  protected retry(): void {
    this.retries.next();
  }
  private updatePreview(): void {
    this.preview.set(draftTotalPreview(this.form.controls.lines.getRawValue()));
  }

  protected submit(event: SubmitEvent): void {
    event.preventDefault();
    const state = this.state();
    if (this.pending() || state.kind !== 'loaded') return;
    this.form.markAllAsTouched();
    if (this.form.controls.lines.length === 0) {
      this.lineError.set('Adicione pelo menos um produto ao pedido.');
      this.formElement()
        ?.nativeElement.querySelector<HTMLInputElement>('app-product-picker input')
        ?.focus();
      return;
    }
    if (this.form.invalid) {
      this.formElement()
        ?.nativeElement.querySelector<HTMLInputElement>('input.ng-invalid')
        ?.focus();
      return;
    }
    const value = this.form.getRawValue();
    const request: SaveDraftRequest = {
      customerId: value.customerId,
      lines: value.lines.map((line) => ({
        ...line,
        quantity: normalizeDraftDecimal(line.quantity),
        unitPrice: normalizeDraftDecimal(line.unitPrice),
      })),
    };
    this.pending.set(true);
    this.failure.set(null);
    const save = state.order
      ? this.api.update(state.order.id, request)
      : this.api.register(request);
    save
      .pipe(
        finalize(() => this.pending.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (order) => void this.router.navigate(['/sales', order.id]),
        error: (error: unknown) => this.failure.set(mapHttpError(error)),
      });
  }
}
