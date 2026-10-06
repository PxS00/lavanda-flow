import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import {
  ActivatedRoute,
  ParamMap,
  Router,
  convertToParamMap,
  provideRouter,
} from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { InventoryItemApiService } from '../../catalog/data-access/inventory-item-api.service';
import { CustomerApiService } from '../../customers/data-access/customer-api.service';
import { OrderApiService } from '../data-access/order-api.service';
import { OrderDto } from '../data-access/order.dto';
import { order, product } from '../testing/order-fixture';
import { OrderFormPage } from './order-form-page';

describe('OrderFormPage', () => {
  let fixture: ComponentFixture<OrderFormPage>;
  let params: Subject<ParamMap>;
  let saved: Subject<OrderDto>;
  let api: {
    getById: ReturnType<typeof vi.fn>;
    register: ReturnType<typeof vi.fn>;
    update: ReturnType<typeof vi.fn>;
  };
  let navigate: ReturnType<typeof vi.spyOn>;
  const customer = {
    id: 'customer',
    name: 'Ana',
    phone: null,
    email: null,
    active: true,
    createdAt: '',
    updatedAt: '',
  };
  beforeEach(async () => {
    params = new Subject<ParamMap>();
    saved = new Subject<OrderDto>();
    api = {
      getById: vi.fn(() => of(order)),
      register: vi.fn(() => saved),
      update: vi.fn(() => saved),
    };
    await TestBed.configureTestingModule({
      imports: [OrderFormPage],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap: params } },
        { provide: OrderApiService, useValue: api },
        {
          provide: CustomerApiService,
          useValue: { search: vi.fn(() => of({ content: [customer], totalElements: 1, page: 0 })) },
        },
        {
          provide: InventoryItemApiService,
          useValue: { search: vi.fn(() => of({ content: [product], totalElements: 1, page: 0 })) },
        },
      ],
    }).compileComponents();
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(OrderFormPage);
    fixture.detectChanges();
  });
  function create() {
    params.next(convertToParamMap({}));
    fixture.detectChanges();
  }
  function button(text: string) {
    return (
      Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[]
    ).find((b) => b.textContent?.includes(text))!;
  }
  function add() {
    button('Perfume').click();
    fixture.detectChanges();
  }
  function fill(quantity = '1', unitPrice = '1,2350') {
    const line = fixture.componentInstance.form.controls.lines.at(0);
    line.controls.quantity.setValue(quantity);
    line.controls.unitPrice.setValue(unitPrice);
    fixture.detectChanges();
  }
  function submit() {
    fixture.nativeElement
      .querySelector('form')
      .dispatchEvent(new SubmitEvent('submit', { bubbles: true, cancelable: true }));
    fixture.detectChanges();
  }

  it('creates without customer using exact normalized strings and shows provisional rounding plus stock disclaimer', () => {
    create();
    add();
    fill();
    expect(fixture.nativeElement.textContent).toContain('Total provisório: R$ 1,24');
    expect(fixture.nativeElement.textContent).toContain('não reserva estoque');
    submit();
    expect(api.register).toHaveBeenCalledWith({
      customerId: null,
      lines: [{ id: null, itemId: 'item', quantity: '1', unitPrice: '1.2350' }],
    });
    expect(navigate).not.toHaveBeenCalled();
    saved.next(order);
    expect(navigate).toHaveBeenCalledWith(['/sales', order.id]);
  });
  it('associates an active customer and allows clearing it without losing lines', () => {
    create();
    add();
    fill();
    button('Ana').click();
    fixture.detectChanges();
    submit();
    expect(api.register.mock.calls[0][0].customerId).toBe('customer');
    saved.error(new HttpErrorResponse({ status: 500 }));
    fixture.detectChanges();
    button('Deixar sem cliente').click();
    fixture.detectChanges();
    expect(fixture.componentInstance.form.controls.customerId.value).toBeNull();
    expect(fixture.componentInstance.form.controls.lines.length).toBe(1);
  });
  it('prefills edit values and preserves stable line IDs in the replacement request', () => {
    params.next(convertToParamMap({ orderId: order.id }));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Editar pedido');
    expect(fixture.componentInstance.form.controls.lines.at(0).controls.id.value).toBe('line');
    fill('2.000001', '3.4567');
    submit();
    expect(api.update).toHaveBeenCalledWith(order.id, {
      customerId: null,
      lines: [{ id: 'line', itemId: 'item', quantity: '2.000001', unitPrice: '3.4567' }],
    });
    expect(api.register).not.toHaveBeenCalled();
  });
  it('manages lines and rejects duplicate item selection', () => {
    create();
    add();
    add();
    expect(fixture.componentInstance.form.controls.lines.length).toBe(1);
    expect(fixture.nativeElement.textContent).toContain('Este produto já está no pedido');
    button('Remover produto').click();
    fixture.detectChanges();
    expect(fixture.componentInstance.form.controls.lines.length).toBe(0);
    submit();
    expect(api.register).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Adicione pelo menos um produto');
  });
  it.each([
    ['0', '1'],
    ['-1', '1'],
    ['1.0000001', '1'],
    ['10000000000000', '1'],
    ['1', '-1'],
    ['1', '1.00001'],
    ['1', '1000000000000000'],
  ])('rejects invalid quantity/price %s/%s with feedback and input focus', (quantity, price) => {
    create();
    add();
    fill(quantity, price);
    submit();
    expect(api.register).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('mat-error')).not.toBeNull();
    expect(document.activeElement?.getAttribute('formControlName')).toBe(
      quantity === '1' ? 'unitPrice' : 'quantity',
    );
  });
  it.each(['create', 'edit'])(
    'prevents duplicate %s writes and preserves entered values on recoverable failure',
    (mode) => {
      if (mode === 'create') {
        create();
        add();
      } else {
        params.next(convertToParamMap({ orderId: order.id }));
        fixture.detectChanges();
      }
      fill('1234567890123.123456', '0.0001');
      submit();
      submit();
      const save = mode === 'create' ? api.register : api.update;
      expect(save).toHaveBeenCalledTimes(1);
      expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBe(true);
      saved.error(new HttpErrorResponse({ status: 500 }));
      fixture.detectChanges();
      expect(fixture.componentInstance.form.controls.lines.at(0).controls.quantity.value).toBe(
        '1234567890123.123456',
      );
      expect(fixture.nativeElement.textContent).toContain('Os dados foram preservados');
      expect(navigate).not.toHaveBeenCalled();
      save.mockReturnValueOnce(of(order));
      submit();
      expect(save).toHaveBeenCalledTimes(2);
      expect(navigate).toHaveBeenCalled();
    },
  );
  it('keeps keyboard focus on added/remaining lines and the selector when all lines are removed', () => {
    create();
    add();
    expect(document.activeElement?.getAttribute('formControlName')).toBe('quantity');
    button('Remover produto').click();
    fixture.detectChanges();
    expect(document.activeElement).toBe(
      fixture.nativeElement.querySelector('app-product-picker input'),
    );
  });
  it('localizes backend overflow feedback without losing precise values', () => {
    create();
    add();
    fill('100.000037', '999999630000136.8999');
    api.register.mockReturnValueOnce(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: {
              timestamp: '2026-10-06T12:00:00Z',
              status: 400,
              error: 'Bad Request',
              code: 'VALIDATION_ERROR',
              message: 'Invalid draft order',
              path: '/api/v1/sales',
              details: { 'lines[0].amount': 'invalid sign, precision or numeric overflow' },
            },
          }),
      ),
    );
    submit();
    expect(fixture.nativeElement.textContent).toContain(
      'Linha do pedido: Verifique o valor informado.',
    );
    expect(fixture.nativeElement.textContent).not.toContain('numeric overflow');
    expect(fixture.componentInstance.form.controls.lines.at(0).controls.unitPrice.value).toBe(
      '999999630000136.8999',
    );
  });
  it('shows a failed edit load and retries before displaying the form', () => {
    api.getById.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 404 })));
    params.next(convertToParamMap({ orderId: order.id }));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('form')).toBeNull();
    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
    button('Tentar novamente').click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('form')).not.toBeNull();
  });

  it('clears feedback from the previous draft when navigating to another order', () => {
    params.next(convertToParamMap({ orderId: order.id }));
    fixture.detectChanges();
    api.update.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
    submit();
    expect(fixture.nativeElement.textContent).toContain('Os dados foram preservados');
    add();
    expect(fixture.nativeElement.textContent).toContain('Este produto já está no pedido');

    api.getById.mockReturnValueOnce(of({ ...order, id: 'another-order', lines: [] }));
    params.next(convertToParamMap({ orderId: 'another-order' }));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('Os dados foram preservados');
    expect(fixture.nativeElement.textContent).not.toContain('Este produto já está no pedido');
    expect(fixture.componentInstance.form.controls.lines.length).toBe(0);
  });
});
