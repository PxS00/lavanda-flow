import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject, of } from 'rxjs';
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
      providers: [provideRouter([]), { provide: OrderApiService, useValue: { search } }],
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
      '.mat-mdc-paginator-navigation-next',
    ) as HTMLButtonElement;
    next.click();
    expect(search).toHaveBeenLastCalledWith({ page: 1, size: 20 });
  });
  it('searches with trimmed ID and date filters, cancels stale responses and validates reversed dates', () => {
    const latest = new Subject<OrderPageDto>();
    search.mockReturnValueOnce(latest);
    fixture.componentInstance.filters.setValue({
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
    fixture.nativeElement.querySelector('.mat-mdc-paginator-navigation-next').click();
    fixture.detectChanges();
    expect(search).toHaveBeenNthCalledWith(2, { page: 1, size: 20 });
    expect(search).toHaveBeenNthCalledWith(3, { page: 0, size: 20 });
    expect(fixture.nativeElement.textContent).toContain('Total oficial');

    fixture.componentInstance.filters.setValue({
      q: 'order',
      from: '2026-10-05',
      to: '2026-10-06',
    });
    search.mockReturnValueOnce(of(page));
    (Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[])
      .find((button) => button.textContent?.trim() === 'Limpar')
      ?.click();
    fixture.detectChanges();
    expect(fixture.componentInstance.filters.getRawValue()).toEqual({ q: '', from: '', to: '' });
    expect(search).toHaveBeenLastCalledWith({
      page: 0,
      size: 20,
      q: undefined,
      from: undefined,
      to: undefined,
    });
  });
});
