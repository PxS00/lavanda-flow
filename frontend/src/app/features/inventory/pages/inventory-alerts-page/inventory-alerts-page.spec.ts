import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, Subject } from 'rxjs';

import { ExpirationAlertsDto, LowStockAlertsDto } from '../../data-access/inventory-alert.dto';
import { InventoryAlertApiService } from '../../data-access/inventory-alert-api.service';
import { InventoryAlertsPage } from './inventory-alerts-page';

describe('InventoryAlertsPage', () => {
  const inventoryItemId = 'bd194732-51cf-4f73-bc5d-3a9f9337adcc';
  const lowStock: LowStockAlertsDto = {
    asOfDate: '2026-09-01',
    alerts: [
      {
        inventoryItemId,
        name: 'Essência de lavanda',
        unitOfMeasure: 'MILLILITER',
        availableQuantity: 25,
        minimumQuantity: 100,
        deficitQuantity: 75,
      },
    ],
  };
  const expiration: ExpirationAlertsDto = {
    asOfDate: '2026-09-01',
    windowDays: 30,
    alerts: [
      {
        inventoryItemId,
        batchId: 'batch-expired',
        lotCode: 'L-001',
        currentQuantity: 12.5,
        expiresAt: '2026-09-01',
        daysUntilExpiration: 0,
        status: 'EXPIRED',
      },
      {
        inventoryItemId,
        batchId: 'batch-expiring',
        lotCode: 'L-002',
        currentQuantity: 5,
        expiresAt: '2026-09-05',
        daysUntilExpiration: 4,
        status: 'EXPIRING_SOON',
      },
    ],
  };

  let fixture: ComponentFixture<InventoryAlertsPage>;
  let lowStockResponse: Subject<LowStockAlertsDto>;
  let expirationResponse: Subject<ExpirationAlertsDto>;
  let getLowStockAlerts: ReturnType<typeof vi.fn<() => Observable<LowStockAlertsDto>>>;
  let getExpirationAlerts: ReturnType<
    typeof vi.fn<(windowDays?: number) => Observable<ExpirationAlertsDto>>
  >;

  beforeEach(async () => {
    lowStockResponse = new Subject<LowStockAlertsDto>();
    expirationResponse = new Subject<ExpirationAlertsDto>();
    getLowStockAlerts = vi.fn(() => lowStockResponse);
    getExpirationAlerts = vi.fn(() => expirationResponse);

    await TestBed.configureTestingModule({
      imports: [InventoryAlertsPage],
      providers: [
        provideRouter([]),
        { provide: InventoryAlertApiService, useValue: { getLowStockAlerts, getExpirationAlerts } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(InventoryAlertsPage);
    fixture.detectChanges();
  });

  it('should load and render backend low-stock alerts without recomputing their quantities', () => {
    expect(getLowStockAlerts).toHaveBeenCalledOnce();

    lowStockResponse.next(lowStock);
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Essência de lavanda');
    expect(text).toContain('25 MILLILITER');
    expect(text).toContain('100 MILLILITER');
    expect(text).toContain('75 MILLILITER');
  });

  it('should distinguish expired from expiring-soon backend statuses', () => {
    expirationResponse.next(expiration);
    fixture.detectChanges();

    const statuses = Array.from(fixture.nativeElement.querySelectorAll('.status')) as HTMLElement[];
    expect(statuses.map((status) => status.textContent?.trim())).toEqual([
      'Vencido',
      'Próximo do vencimento',
    ]);
    expect(statuses[0].classList).toContain('status-expired');
    expect(statuses[1].classList).toContain('status-expiring');
    expect(fixture.nativeElement.textContent).toContain('01/09/2026');
    expect(fixture.nativeElement.textContent).toContain('batch-expired');
  });

  it('should request an explicit zero-day window from the operator', () => {
    expirationResponse.next(expiration);
    fixture.detectChanges();

    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    input.value = '0';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('.window-form') as HTMLFormElement).dispatchEvent(
      new SubmitEvent('submit', { cancelable: true }),
    );

    expect(getExpirationAlerts).toHaveBeenLastCalledWith(0);
  });

  it('should reject a blank window rather than coercing it to zero', () => {
    expirationResponse.next(expiration);
    fixture.detectChanges();

    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    input.value = '   ';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('.window-form') as HTMLFormElement).dispatchEvent(
      new SubmitEvent('submit', { cancelable: true }),
    );
    fixture.detectChanges();

    expect(getExpirationAlerts).toHaveBeenCalledOnce();
    expect(fixture.nativeElement.textContent).toContain('Informe a janela de validade.');
  });

  it('should present empty alert sets as a valid state and link each alert to its item workspace', () => {
    lowStockResponse.next({ ...lowStock, alerts: [] });
    expirationResponse.next({ ...expiration, alerts: [] });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Nenhum alerta de estoque baixo');
    expect(fixture.nativeElement.textContent).toContain('Nenhum alerta de validade');

    const refreshedLowStock = new Subject<LowStockAlertsDto>();
    getLowStockAlerts.mockReturnValue(refreshedLowStock);
    fixture.nativeElement.querySelector('.panel button')?.click();
    refreshedLowStock.next(lowStock);
    fixture.detectChanges();

    const itemLink = fixture.nativeElement.querySelector('a[aria-label="Abrir operação do item Essência de lavanda"]') as HTMLAnchorElement;
    expect(itemLink.getAttribute('href')).toBe(`/inventory/items/${inventoryItemId}`);
  });

  it('should show shared error presentation for backend validation and transport failures', () => {
    lowStockResponse.error(
      new HttpErrorResponse({
        status: 0,
        statusText: 'Unknown Error',
        error: new ProgressEvent('error'),
      }),
    );
    expirationResponse.error(
      new HttpErrorResponse({
        status: 400,
        error: {
          timestamp: '2026-09-01T12:00:00Z', status: 400, error: 'Bad Request',
          code: 'INVALID_REQUEST_PARAMETER', message: 'windowDays must be positive',
          path: '/api/v1/inventory/alerts/expiration', details: null,
        },
      }),
    );
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Não foi possível conectar ao servidor.');
    expect(text).toContain('Um parâmetro informado é inválido.');
    expect(text).not.toContain('windowDays must be positive');
  });
});
