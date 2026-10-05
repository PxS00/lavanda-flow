import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, ParamMap, convertToParamMap, provideRouter } from '@angular/router';
import { Observable, Subject } from 'rxjs';

import { CustomerDto } from '../../data-access/customer.dto';
import { CustomerApiService } from '../../data-access/customer-api.service';
import { CustomerDetailPage } from './customer-detail-page';

describe('CustomerDetailPage', () => {
  const customerId = '53b1fdb4-72ab-41bb-b9e7-381d922d69a8';
  const customer: CustomerDto = {
    id: customerId,
    name: 'Lavanda Supplies',
    phone: '+5511999991234',
    email: 'customers@example.test',
    createdAt: '2026-10-05T12:00:00Z',
    updatedAt: '2026-10-05T12:00:00Z',
    active: true,
  };

  let fixture: ComponentFixture<CustomerDetailPage>;
  let response: Subject<CustomerDto>;
  let routeParams: Subject<ParamMap>;
  let getById: ReturnType<typeof vi.fn<(id: string) => Observable<CustomerDto>>>;

  let saved: Subject<CustomerDto>;
  let activate: ReturnType<typeof vi.fn>;
  let deactivate: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    saved = new Subject<CustomerDto>();
    activate = vi.fn(() => saved);
    deactivate = vi.fn(() => saved);
    response = new Subject<CustomerDto>();
    routeParams = new Subject<ParamMap>();
    getById = vi.fn(() => response);

    await TestBed.configureTestingModule({
      imports: [CustomerDetailPage],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap: routeParams } },
        { provide: CustomerApiService, useValue: { getById, activate, deactivate } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(CustomerDetailPage);
    fixture.detectChanges();
    routeParams.next(convertToParamMap({ customerId }));
    fixture.detectChanges();
  });

  it('should use the direct route ID and show loading feedback', () => {
    expect(getById).toHaveBeenCalledWith(customerId);
    expect(fixture.nativeElement.textContent).toContain('Carregando cliente...');
  });

  it('should render the returned customer', () => {
    response.next(customer);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Lavanda Supplies');
    expect(fixture.nativeElement.querySelector('header p').getAttribute('aria-live')).toBe(
      'polite',
    );
    expect(fixture.nativeElement.textContent).toContain('+5511999991234');
    expect(fixture.nativeElement.textContent).toContain('customers@example.test');
    const editLink = Array.from(fixture.nativeElement.querySelectorAll('a')).find(
      (link) => (link as HTMLAnchorElement).textContent?.trim() === 'Editar cliente',
    ) as HTMLAnchorElement | undefined;

    expect(editLink?.getAttribute('href')).toBe(`/customers/${customerId}/edit`);
  });

  it('should render the mapped not-found state', () => {
    response.error(apiError(404, 'CUSTOMER_NOT_FOUND', 'Customer not found.'));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Cliente não encontrado.');
  });

  it('should render a generic server error and retry', () => {
    response.error(apiError(500, 'INTERNAL_ERROR', 'Internal details'));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(
      'Ocorreu um erro no servidor. Tente novamente.',
    );

    response = new Subject<CustomerDto>();
    getById.mockReturnValue(response);
    const retryButton = Array.from(fixture.nativeElement.querySelectorAll('button')).find(
      (button) => (button as HTMLButtonElement).textContent?.trim() === 'Tentar novamente',
    ) as HTMLButtonElement | undefined;

    retryButton?.click();
    fixture.detectChanges();

    expect(getById).toHaveBeenCalledTimes(2);
    expect(getById).toHaveBeenLastCalledWith(customerId);
  });

  it('should keep the newest customer when route parameters change', () => {
    const firstResponse = response;
    const secondResponse = new Subject<CustomerDto>();
    const secondCustomer: CustomerDto = {
      ...customer,
      id: 'second-customer',
      name: 'Second customer',
    };
    getById.mockReturnValueOnce(secondResponse);

    routeParams.next(convertToParamMap({ customerId: secondCustomer.id }));
    fixture.detectChanges();
    firstResponse.next(customer);
    fixture.detectChanges();

    expect(getById).toHaveBeenLastCalledWith(secondCustomer.id);
    expect(fixture.nativeElement.textContent).toContain('Carregando cliente...');

    secondResponse.next(secondCustomer);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Second customer');
    expect(fixture.nativeElement.textContent).not.toContain('Lavanda Supplies');
  });

  it.each([true, false])(
    'changes active state %s only after backend success and prevents duplicates',
    (active) => {
      response.next({ ...customer, active });
      fixture.detectChanges();
      const button = (
        Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[]
      ).find((b) => b.textContent?.includes(active ? 'Desativar cliente' : 'Ativar cliente'));
      button?.click();
      fixture.detectChanges();
      // A pending button cannot issue a second status action.
      button?.click();
      expect(active ? deactivate : activate).toHaveBeenCalledTimes(1);
      expect(active ? deactivate : activate).toHaveBeenCalledWith(customerId);
      expect(fixture.nativeElement.querySelector('header p').textContent).toContain(
        active ? 'Ativo' : 'Inativo',
      );
      saved.next({ ...customer, active: !active });
      saved.complete();
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('header p').textContent).toContain(
        active ? 'Inativo' : 'Ativo',
      );
    },
  );

  it.each([true, false])('preserves active state %s on failure and supports retry', (active) => {
    response.next({ ...customer, active });
    fixture.detectChanges();
    const findButton = () =>
      (Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[]).find(
        (b) => b.textContent?.includes(active ? 'Desativar cliente' : 'Ativar cliente'),
      );
    findButton()?.click();
    saved.error(new HttpErrorResponse({ status: 500 }));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('header p').textContent).toContain(
      active ? 'Ativo' : 'Inativo',
    );
    expect(fixture.nativeElement.textContent).toContain('Ocorreu um erro no servidor');
    expect(findButton()?.disabled).toBe(false);
    saved = new Subject<CustomerDto>();
    (active ? deactivate : activate).mockReturnValueOnce(saved);
    findButton()?.click();
    expect(active ? deactivate : activate).toHaveBeenCalledTimes(2);
  });

  function apiError(status: number, code: string, message: string): HttpErrorResponse {
    return new HttpErrorResponse({
      status,
      error: {
        timestamp: '2026-09-01T12:00:00Z',
        status,
        error: status === 404 ? 'Not Found' : 'Internal Server Error',
        code,
        message,
        path: `/api/v1/customers/${customerId}`,
        details: {},
      },
    });
  }
});
