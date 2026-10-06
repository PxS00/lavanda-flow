import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { OrderApiService } from '../data-access/order-api.service';
import { OrderDto } from '../data-access/order.dto';
import { order } from '../testing/order-fixture';
import { OrderDetailPage } from './order-detail-page';

describe('OrderDetailPage', () => {
  it('confirms explicitly, blocks duplicate submissions and displays the persisted confirmed result', async () => {
    const response = new Subject<OrderDto>();
    const confirm = vi.fn(() => response.asObservable());
    await TestBed.configureTestingModule({
      imports: [OrderDetailPage],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { paramMap: of(convertToParamMap({ orderId: order.id })) },
        },
        { provide: OrderApiService, useValue: { getById: () => of(order), confirm } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(OrderDetailPage);
    fixture.detectChanges();
    const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    button.click();
    fixture.detectChanges();
    expect(button.disabled).toBe(true);
    button.click();
    // The method guard also protects programmatic/reentrant submissions.
    (fixture.componentInstance as unknown as { confirm(): void }).confirm();
    expect(confirm).toHaveBeenCalledExactlyOnceWith(order.id);
    expect(fixture.nativeElement.textContent).toContain('Rascunho');
    response.next({
      ...order,
      status: 'CONFIRMED',
      confirmedAt: order.updatedAt,
      lines: [
        {
          ...order.lines[0]!,
          allocations: [{ batchId: 'batch', movementId: 'movement', quantity: '1.000000' }],
        },
      ],
    });
    response.complete();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Venda confirmada');
    expect(fixture.nativeElement.textContent).toContain('batch');
    expect(fixture.nativeElement.textContent).toContain('movement');
    expect(fixture.nativeElement.querySelector('a[href$="/edit"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('button')).toBeNull();
    expect(document.activeElement).toBe(fixture.nativeElement.querySelector('h2'));
  });

  it.each([409, 422, 500, 0])(
    'preserves the draft after status %s and allows an explicit retry using the same identity',
    async (status) => {
      const failure = new HttpErrorResponse({
        status,
        error:
          status === 409 || status === 422
            ? {
                timestamp: order.updatedAt,
                status,
                error: status === 409 ? 'Conflict' : 'Unprocessable Entity',
                code: status === 409 ? 'ORDER_LOCK_CONFLICT' : 'INSUFFICIENT_ELIGIBLE_STOCK',
                message:
                  status === 409
                    ? 'Order operation could not acquire its required locks'
                    : 'Insufficient eligible stock',
                path: '/api/v1/sales/order/confirm',
              }
            : undefined,
      });
      const confirm = vi
        .fn()
        .mockReturnValueOnce(throwError(() => failure))
        .mockReturnValueOnce(of({ ...order, status: 'CONFIRMED', confirmedAt: order.updatedAt }));
      await TestBed.configureTestingModule({
        imports: [OrderDetailPage],
        providers: [
          provideRouter([]),
          {
            provide: ActivatedRoute,
            useValue: { paramMap: of(convertToParamMap({ orderId: order.id })) },
          },
          { provide: OrderApiService, useValue: { getById: () => of(order), confirm } },
        ],
      }).compileComponents();
      const fixture = TestBed.createComponent(OrderDetailPage);
      fixture.detectChanges();
      (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
      fixture.detectChanges();
      expect(confirm).toHaveBeenCalledTimes(1);
      expect(fixture.nativeElement.textContent).toContain('Rascunho');
      expect(fixture.nativeElement.textContent).not.toContain('Venda confirmada');
      if (status === 409)
        expect(fixture.nativeElement.textContent).toContain(
          'O pedido ou estoque está sendo usado por outra operação. Tente novamente.',
        );
      expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
      if (status === 422) expect(fixture.nativeElement.textContent).toContain('estoque elegível');
      const retry = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
      expect(retry.disabled).toBe(false);
      expect(retry.textContent).toContain('Tentar confirmar novamente');
      retry.click();
      fixture.detectChanges();
      expect(confirm.mock.calls).toEqual([[order.id], [order.id]]);
      expect(fixture.nativeElement.textContent).toContain('Venda confirmada');
      expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeNull();
    },
  );

  it('renders loading, not found, explicit retry and official exact values with an edit link', async () => {
    const response = new Subject<OrderDto>();
    const getById = vi.fn(() => response.asObservable());
    await TestBed.configureTestingModule({
      imports: [OrderDetailPage],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { paramMap: of(convertToParamMap({ orderId: order.id })) },
        },
        { provide: OrderApiService, useValue: { getById } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(OrderDetailPage);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Carregando pedido');
    response.error(new HttpErrorResponse({ status: 404 }));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
    getById.mockReturnValueOnce(of(order));
    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(getById).toHaveBeenCalledTimes(2);
    expect(fixture.nativeElement.textContent).toContain('Sem cliente associado');
    expect(fixture.nativeElement.textContent).toContain('1,235');
    expect(fixture.nativeElement.textContent).toContain('R$ 1,24');
    expect(fixture.nativeElement.textContent).toContain('não reserva estoque');
    expect(fixture.nativeElement.querySelector('a[href="/sales/order/edit"]')).not.toBeNull();
  });
});
