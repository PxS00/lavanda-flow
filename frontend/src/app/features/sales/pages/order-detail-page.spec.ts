import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { Subject, of } from 'rxjs';
import { OrderApiService } from '../data-access/order-api.service';
import { OrderDto } from '../data-access/order.dto';
import { order } from '../testing/order-fixture';
import { OrderDetailPage } from './order-detail-page';

describe('OrderDetailPage', () => {
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
