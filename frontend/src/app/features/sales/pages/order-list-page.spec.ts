import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject, of } from 'rxjs';
import { CustomerApiService } from '../../customers/data-access/customer-api.service';
import { CustomerPicker } from '../ui/customer-picker';
import { By } from '@angular/platform-browser';
import { MatSelect } from '@angular/material/select';
import { OrderApiService } from '../data-access/order-api.service';
import { OrderPageDto } from '../data-access/order.dto';
import { order } from '../testing/order-fixture';
import { OrderListPage } from './order-list-page';

const page: OrderPageDto = {
  content: [order],
  page: 0,
  size: 20,
  totalElements: 40,
  totalPages: 2,
};
describe('OrderListPage', () => {
  let fixture: ComponentFixture<OrderListPage>;
  let response: Subject<OrderPageDto>;
  let search: ReturnType<typeof vi.fn>;
  beforeEach(async () => {
    response = new Subject<OrderPageDto>();
    search = vi.fn(() => response);
    await TestBed.configureTestingModule({
      imports: [OrderListPage],
      providers: [
        provideRouter([]),
        { provide: OrderApiService, useValue: { search } },
        {
          provide: CustomerApiService,
          useValue: {
            search: () => of({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
          },
        },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(OrderListPage);
    fixture.detectChanges();
  });
  function apply() {
    fixture.nativeElement
      .querySelector('form')
      .dispatchEvent(new SubmitEvent('submit', { bubbles: true, cancelable: true }));
    fixture.detectChanges();
  }
  it('shows loading then server total, optional customer, detail links and pagination', () => {
    expect(search).toHaveBeenCalledWith({ page: 0, size: 20 });
    expect(fixture.nativeElement.textContent).toContain('Carregando pedidos');
    response.next(page);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Sem cliente associado');
    expect(fixture.nativeElement.textContent).toContain('Total oficial: R$ 1,24');
    expect(fixture.nativeElement.querySelector('a[href="/sales/order"]')).not.toBeNull();
    const next = fixture.nativeElement.querySelector(
      'mat-paginator[aria-label="Páginas de pedidos"] .mat-mdc-paginator-navigation-next',
    ) as HTMLButtonElement;
    next.click();
    expect(search).toHaveBeenLastCalledWith({ page: 1, size: 20 });
  });
  it('searches with trimmed ID and date filters, cancels stale responses and validates reversed dates', () => {
    const latest = new Subject<OrderPageDto>();
    search.mockReturnValueOnce(latest);
    fixture.componentInstance.filters.patchValue({
      q: ' order ',
      from: '2026-10-05',
      to: '2026-10-06',
    });
    apply();
    expect(search).toHaveBeenLastCalledWith({
      page: 0,
      size: 20,
      q: 'order',
      from: '2026-10-05',
      to: '2026-10-06',
    });
    latest.next({ ...page, content: [] });
    response.next(page);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Nenhum pedido encontrado');
    fixture.componentInstance.filters.controls.from.setValue('2026-10-07');
    apply();
    expect(search).toHaveBeenCalledTimes(2);
    expect(fixture.nativeElement.textContent).toContain('data inicial deve ser anterior');
  });
  it('shows empty and recoverable failure then retries the same query', () => {
    response.next({ ...page, content: [], totalElements: 0, totalPages: 0 });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Nenhum pedido encontrado');
    response.error(new HttpErrorResponse({ status: 500 }));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Ocorreu um erro no servidor');
    search.mockReturnValueOnce(of(page));
    (Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[])
      .find((b) => b.textContent?.includes('Tentar novamente'))
      ?.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Total oficial');
  });

  it('recovers an out-of-range page once and clears search/date filters', () => {
    response.next(page);
    fixture.detectChanges();
    search.mockReturnValueOnce(of({ ...page, page: 1, content: [], totalPages: 1 }));
    search.mockReturnValueOnce(of({ ...page, totalPages: 1 }));
    fixture.nativeElement
      .querySelector(
        'mat-paginator[aria-label="Páginas de pedidos"] .mat-mdc-paginator-navigation-next',
      )
      .click();
    fixture.detectChanges();
    expect(search).toHaveBeenNthCalledWith(2, { page: 1, size: 20 });
    expect(search).toHaveBeenNthCalledWith(3, { page: 0, size: 20 });
    expect(fixture.nativeElement.textContent).toContain('Total oficial');

    fixture.componentInstance.filters.patchValue({
      q: 'order',
      from: '2026-10-05',
      to: '2026-10-06',
    });
    search.mockReturnValueOnce(of(page));
    (Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[])
      .find((button) => button.textContent?.trim() === 'Limpar')
      ?.click();
    fixture.detectChanges();
    expect(fixture.componentInstance.filters.getRawValue()).toEqual({
      q: '',
      from: '',
      to: '',
      status: 'DRAFT',
      confirmedFrom: '',
      confirmedTo: '',
    });
    expect(search).toHaveBeenLastCalledWith({
      page: 0,
      size: 20,
      q: undefined,
      from: undefined,
      to: undefined,
    });
  });
  it('discovers confirmed and cancelled records with customer/date filters, paging and reset', () => {
    const customer = {
      id: 'customer',
      name: 'Histórica',
      active: false,
      phone: null,
      email: null,
      createdAt: '',
      updatedAt: '',
    };
    const picker = fixture.debugElement.query(By.directive(CustomerPicker))
      .componentInstance as CustomerPicker;
    expect(picker.activeOnly()).toBe(false);
    picker.selected.emit(customer);
    fixture.componentInstance.filters.patchValue({
      status: 'CONFIRMED',
      confirmedFrom: '2026-10-01',
      confirmedTo: '2026-10-02',
      from: '2026-09-01',
    });
    apply();
    const query = {
      page: 0,
      size: 20,
      status: 'CONFIRMED',
      customerId: 'customer',
      confirmedFrom: '2026-10-01',
      confirmedTo: '2026-10-02',
      from: '2026-09-01',
      to: undefined,
      q: undefined,
    };
    expect(search).toHaveBeenLastCalledWith(query);
    response.next({
      ...page,
      content: [
        { ...order, status: 'CONFIRMED', customerName: 'Snapshot', confirmedAt: order.updatedAt },
      ],
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Venda confirmada');
    expect(fixture.nativeElement.textContent).toContain('Snapshot');
    expect(fixture.nativeElement.querySelector('a[href="/sales/order"]')).not.toBeNull();
    fixture.nativeElement
      .querySelector(
        'mat-paginator[aria-label="Páginas de pedidos"] .mat-mdc-paginator-navigation-next',
      )
      .click();
    expect(search).toHaveBeenLastCalledWith({ ...query, page: 1 });
    fixture.componentInstance.filters.patchValue({ status: 'CANCELLED' });
    apply();
    expect(search).toHaveBeenLastCalledWith(
      expect.objectContaining({ status: 'CANCELLED', page: 0 }),
    );
    expect(search.mock.calls.at(-1)?.[0]).not.toHaveProperty('confirmedFrom');
    response.next({ ...page, content: [{ ...order, status: 'CANCELLED' }] });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Cancelado');
    (Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[])
      .find((b) => b.textContent?.trim() === 'Limpar')
      ?.click();
    expect(search).toHaveBeenLastCalledWith({
      page: 0,
      size: 20,
      q: undefined,
      from: undefined,
      to: undefined,
    });
    expect(fixture.componentInstance.filters.controls.status.value).toBe('DRAFT');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('Cliente selecionado');
  });

  it('rejects reversed confirmation dates without requesting a page', () => {
    fixture.componentInstance.filters.patchValue({
      status: 'CONFIRMED',
      confirmedFrom: '2026-10-02',
      confirmedTo: '2026-10-01',
    });
    apply();
    expect(search).toHaveBeenCalledTimes(1);
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain(
      'data inicial de confirmação',
    );
  });
  it('keeps confirmed history filters through empty/error/retry and clears confirmation dates on status change', () => {
    fixture.componentInstance.filters.patchValue({
      status: 'CONFIRMED',
      confirmedFrom: '2026-10-01',
    });
    apply();
    response.next({ ...page, content: [], totalElements: 0, totalPages: 0 });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Nenhum pedido encontrado');
    response.error(new HttpErrorResponse({ status: 500 }));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
    search.mockReturnValueOnce(of({ ...page, content: [{ ...order, status: 'CONFIRMED' }] }));
    (Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[])
      .find((b) => b.textContent?.includes('Tentar novamente'))
      ?.click();
    expect(search).toHaveBeenLastCalledWith(
      expect.objectContaining({ status: 'CONFIRMED', confirmedFrom: '2026-10-01', page: 0 }),
    );
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Venda confirmada');
    fixture.componentInstance.filters.controls.status.setValue('CANCELLED');
    fixture.debugElement
      .query(By.directive(MatSelect))
      .triggerEventHandler('selectionChange', { value: 'CANCELLED' });
    expect(fixture.componentInstance.filters.controls.confirmedFrom.value).toBe('');
    fixture.detectChanges();
    expect(
      fixture.nativeElement.querySelector('input[formControlName="confirmedFrom"]'),
    ).toBeNull();
  });
});
