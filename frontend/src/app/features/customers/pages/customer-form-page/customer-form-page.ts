import {
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
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
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  Observable,
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

import { mapHttpError } from '../../../../core/http/map-http-error';
import { localizeFieldError } from '../../../../core/http/localize-ui-error';
import { UiError } from '../../../../core/http/ui-error';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../../shared/ui/loading-state/loading-state';
import { CustomerDto, CustomerRequest } from '../../data-access/customer.dto';
import { CustomerApiService } from '../../data-access/customer-api.service';

type CustomerFormState =
  | { readonly kind: 'loading' }
  | { readonly kind: 'loaded'; readonly customer: CustomerDto | null }
  | { readonly kind: 'error'; readonly error: UiError };
type CustomerField = 'name' | 'phone' | 'email';

@Component({
  selector: 'app-customer-form-page',
  imports: [
    ErrorState,
    LoadingState,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    ReactiveFormsModule,
    RouterLink,
  ],
  templateUrl: './customer-form-page.html',
  styleUrl: './customer-form-page.scss',
})
export class CustomerFormPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly customerApi = inject(CustomerApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly retries = new Subject<void>();
  private readonly formElement = viewChild<ElementRef<HTMLFormElement>>('contactForm');

  readonly editForm = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: nameValidator }),
    phone: new FormControl('', { nonNullable: true, validators: phoneValidator }),
    email: new FormControl('', { nonNullable: true, validators: emailValidator }),
  });
  protected readonly state = signal<CustomerFormState>({ kind: 'loading' });
  protected readonly stateCustomer = computed(() => {
    const state = this.state();
    return state.kind === 'loaded' ? state.customer : null;
  });
  protected readonly isSubmitting = signal(false);
  protected readonly submissionError = signal<UiError | null>(null);

  constructor() {
    this.route.paramMap
      .pipe(
        map((params) => params.get('customerId')),
        distinctUntilChanged(),
        switchMap((id) =>
          this.retries.pipe(
            startWith(undefined),
            tap(() => {
              this.state.set({ kind: 'loading' });
              this.submissionError.set(null);
            }),
            switchMap(() =>
              this.loadCustomer(id).pipe(
                map((customer): CustomerFormState => ({ kind: 'loaded', customer })),
                catchError((error: unknown) =>
                  of<CustomerFormState>({ kind: 'error', error: mapHttpError(error) }),
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
            name: state.customer?.name ?? '',
            phone: state.customer?.phone ?? '',
            email: state.customer?.email ?? '',
          });
        }
      });
  }

  private loadCustomer(id: string | null): Observable<CustomerDto | null> {
    return id === null ? of(null) : this.customerApi.getById(id);
  }

  protected retry(): void {
    this.retries.next();
  }

  protected submit(event: SubmitEvent): void {
    event.preventDefault();
    const state = this.state();
    if (this.isSubmitting() || state.kind !== 'loaded') {
      return;
    }
    this.editForm.markAllAsTouched();
    if (this.editForm.invalid) {
      const field = (['name', 'phone', 'email'] as const).find(
        (name) => this.editForm.controls[name].invalid,
      );
      this.formElement()
        ?.nativeElement.querySelector<HTMLInputElement>(`input[formControlName="${field}"]`)
        ?.focus();
      return;
    }
    const value = this.editForm.getRawValue();
    const request: CustomerRequest = {
      name: value.name.trim(),
      phone: value.phone.trim() || null,
      email: value.email.trim() || null,
    };
    const save =
      state.customer === null
        ? this.customerApi.register(request)
        : this.customerApi.update(state.customer.id, request);
    this.isSubmitting.set(true);
    this.submissionError.set(null);
    save
      .pipe(
        finalize(() => this.isSubmitting.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (customer) => void this.router.navigate(['/customers', customer.id]),
        error: (error: unknown) => this.submissionError.set(mapHttpError(error)),
      });
  }

  protected backendFieldError(field: CustomerField): string | undefined {
    return localizeFieldError(this.submissionError(), field);
  }
}

function nameValidator(control: AbstractControl<string>): ValidationErrors | null {
  const value = control.value.trim();
  return !value || value.length > 160 ? { name: true } : null;
}

function phoneValidator(control: AbstractControl<string>): ValidationErrors | null {
  const value = control.value.trim();
  return !value || /^\+?[0-9]{7,15}$/.test(value.replace(/[ ().-]/g, '')) ? null : { phone: true };
}

function emailValidator(control: AbstractControl<string>): ValidationErrors | null {
  const value = control.value.trim();
  return !value
    ? null
    : value.length > 254 || Validators.email(new FormControl(value))
      ? { email: true }
      : null;
}
