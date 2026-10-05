import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import {
  ActivatedRoute,
  ParamMap,
  convertToParamMap,
  provideRouter,
  Router,
} from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { CustomerDto } from '../../data-access/customer.dto';
import { CustomerApiService } from '../../data-access/customer-api.service';
import { CustomerFormPage } from './customer-form-page';

const customer: CustomerDto = {
  id: 'customer-id',
  name: 'Ana',
  phone: '+5511999991234',
  email: 'ana@example.test',
  active: false,
  createdAt: '2026-10-05T12:00:00Z',
  updatedAt: '2026-10-05T12:00:00Z',
};

describe('CustomerFormPage', () => {
  let fixture: ComponentFixture<CustomerFormPage>;
  let routeParams: Subject<ParamMap>;
  let response: Subject<CustomerDto>;
  let saved: Subject<CustomerDto>;
  let api: {
    getById: ReturnType<typeof vi.fn>;
    register: ReturnType<typeof vi.fn>;
    update: ReturnType<typeof vi.fn>;
  };
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    routeParams = new Subject<ParamMap>();
    response = new Subject<CustomerDto>();
    saved = new Subject<CustomerDto>();
    api = {
      getById: vi.fn(() => response),
      register: vi.fn(() => saved),
      update: vi.fn(() => saved),
    };
    await TestBed.configureTestingModule({
      imports: [CustomerFormPage],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap: routeParams } },
        { provide: CustomerApiService, useValue: api },
      ],
    }).compileComponents();
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(CustomerFormPage);
    fixture.detectChanges();
  });

  function openCreate(): void {
    routeParams.next(convertToParamMap({}));
    fixture.detectChanges();
  }
  function openEdit(): void {
    routeParams.next(convertToParamMap({ customerId: customer.id }));
    response.next(customer);
    fixture.detectChanges();
  }
  function submit(): void {
    (fixture.nativeElement.querySelector('form') as HTMLFormElement).dispatchEvent(
      new SubmitEvent('submit', { bubbles: true, cancelable: true }),
    );
    fixture.detectChanges();
  }

  it('creates a name-only contact, normalizes blank optionals and navigates only after success', () => {
    openCreate();
    fixture.componentInstance.editForm.setValue({ name: ' Ana ', phone: ' ', email: '' });
    submit();
    expect(api.register).toHaveBeenCalledWith({ name: 'Ana', phone: null, email: null });
    expect(api.getById).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
    saved.next(customer);
    expect(navigate).toHaveBeenCalledWith(['/customers', customer.id]);
  });

  it('loads an inactive contact and replaces contact fields without changing active state', () => {
    openEdit();
    expect(fixture.nativeElement.textContent).toContain('Editar cliente');
    expect(fixture.componentInstance.editForm.getRawValue()).toEqual({
      name: customer.name,
      phone: customer.phone,
      email: customer.email,
    });
    fixture.componentInstance.editForm.setValue({
      name: 'Ana Maria',
      phone: '',
      email: ' new@example.test ',
    });
    submit();
    expect(api.update).toHaveBeenCalledWith(customer.id, {
      name: 'Ana Maria',
      phone: null,
      email: 'new@example.test',
    });
    expect(api.register).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
    saved.next({ ...customer, name: 'Ana Maria' });
    expect(navigate).toHaveBeenCalledWith(['/customers', customer.id]);
  });

  it.each(['create', 'edit'])('prevents duplicate %s submission while pending', (mode) => {
    if (mode === 'create') {
      openCreate();
      fixture.componentInstance.editForm.controls.name.setValue('Ana');
    } else {
      openEdit();
    }
    submit();
    submit();
    expect(mode === 'create' ? api.register : api.update).toHaveBeenCalledTimes(1);
    expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBe(true);
    expect(fixture.nativeElement.querySelector('input').readOnly).toBe(true);
  });

  it.each([
    ['name', ' ', 'Nome é obrigatório'],
    ['name', 'a'.repeat(161), '160 caracteres'],
    ['phone', '123456', '7 a 15 dígitos'],
    ['phone', '+1234567890123456', '7 a 15 dígitos'],
    ['phone', '123a4567', '7 a 15 dígitos'],
    ['phone', '123+4567', '7 a 15 dígitos'],
    ['email', 'invalid', 'e-mail válido'],
    ['email', 'a'.repeat(255) + '@example.test', '254 caracteres'],
  ] as const)(
    'shows immediate feedback for invalid %s and focuses the first invalid input',
    (field, value, message) => {
      openCreate();
      fixture.componentInstance.editForm.controls.name.setValue('Ana');
      fixture.componentInstance.editForm.controls[field].setValue(value);
      submit();
      expect(api.register).not.toHaveBeenCalled();
      expect(fixture.nativeElement.textContent).toContain(message);
      expect(document.activeElement?.getAttribute('formControlName')).toBe(field);
    },
  );

  it.each(['1234567', '+123456789012345', '+55 (11) 99999-1234.'])(
    'accepts supported phone input %s',
    (phone) => {
      openCreate();
      fixture.componentInstance.editForm.setValue({ name: 'Ana', phone, email: '' });
      submit();
      expect(api.register).toHaveBeenCalledWith({ name: 'Ana', phone, email: null });
    },
  );

  it.each(['create', 'edit'])(
    'preserves %s input after recoverable server failure and permits retry',
    (mode) => {
      if (mode === 'create') {
        openCreate();
      } else {
        openEdit();
      }
      const save = mode === 'create' ? api.register : api.update;
      save.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
      fixture.componentInstance.editForm.setValue({ name: 'Preservado', phone: '', email: '' });
      submit();
      expect(fixture.componentInstance.editForm.controls.name.value).toBe('Preservado');
      expect(navigate).not.toHaveBeenCalled();
      expect(fixture.nativeElement.textContent).toContain('Ocorreu um erro no servidor');
      expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBe(false);
      submit();
      expect(save).toHaveBeenCalledTimes(2);
    },
  );

  it('renders backend field validation in pt-BR while retaining entered values', () => {
    openCreate();
    api.register.mockReturnValueOnce(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: {
              code: 'VALIDATION_ERROR',
              status: 400,
              message: 'Request validation failed',
              error: 'Bad Request',
              timestamp: '2026-10-05T12:00:00Z',
              path: '/api/v1/customers',
              details: { email: 'must be a well-formed email address' },
            },
          }),
      ),
    );
    fixture.componentInstance.editForm.setValue({
      name: 'Ana',
      phone: '',
      email: 'ana@example.test',
    });
    submit();
    expect(fixture.nativeElement.textContent).toContain('Verifique o valor informado.');
    expect(fixture.nativeElement.textContent).not.toContain('well-formed');
    expect(fixture.componentInstance.editForm.controls.email.value).toBe('ana@example.test');
    expect(
      fixture.nativeElement
        .querySelector('input[formControlName="email"]')
        .getAttribute('aria-describedby'),
    ).toContain('customer-email-backend-error');
  });

  it('shows loading and failed detail retrieval, then retries and prefills the form', () => {
    routeParams.next(convertToParamMap({ customerId: customer.id }));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Carregando cliente');
    response.error(new HttpErrorResponse({ status: 404 }));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
    api.getById.mockReturnValueOnce(of(customer));
    (Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[])
      .find((b) => b.textContent?.includes('Tentar novamente'))
      ?.click();
    fixture.detectChanges();
    expect(fixture.componentInstance.editForm.controls.name.value).toBe('Ana');
  });
});
