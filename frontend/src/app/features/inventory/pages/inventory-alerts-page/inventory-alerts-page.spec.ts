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
        availableQuantity: '25',
        minimumQuantity: '100',
        deficitQuantity: '75',
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
        currentQuantity: '12.5',
        expiresAt: '2026-09-01',
        daysUntilExpiration: 0,
        status: 'EXPIRED',
      },
      {
        inventoryItemId,
        batchId: 'batch-expiring',
        lotCode: 'L-002',
        currentQuantity: '5',
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

  it('should render localized quantities and exact low-stock actions without recomputing their quantities', () => {
    expect(getLowStockAlerts).toHaveBeenCalledOnce();

    lowStockResponse.next(lowStock);
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Essência de lavanda');
    expect(text).toContain('25 Mililitro');
    expect(text).toContain('100 Mililitro');
    expect(text).toContain('75 Mililitro');
    expect(text).not.toContain('MILLILITER');
    expect(link('Registrar entrada para Essência de lavanda').getAttribute('href')).toBe(
      `/receipts?inventoryItemId=${inventoryItemId}`,
    );
    expect(link('Abrir estoque de Essência de lavanda').getAttribute('href')).toBe(
      `/inventory/items/${inventoryItemId}`,
    );
  });

  it('should use backend expiration statuses for exact actions without date classification', () => {
    expirationResponse.next({
      ...expiration,
      alerts: [
        { ...expiration.alerts[0], expiresAt: '2027-09-01', daysUntilExpiration: 365 },
        { ...expiration.alerts[1], expiresAt: '2026-01-01', daysUntilExpiration: -243 },
      ],
    });
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
    expect(link('Descartar lote vencido L-001').getAttribute('href')).toBe(
      `/inventory/items/${inventoryItemId}?batchId=batch-expired&maintenance=expired-disposal#batches`,
    );
    expect(link('Abrir lote L-002').getAttribute('href')).toBe(
      `/inventory/items/${inventoryItemId}?batchId=batch-expiring#batches`,
    );
  });

  it('associates the expiration-window helper without changing expiration status metadata', () => {
    expirationResponse.next({ ...expiration, alerts: [] });
    fixture.detectChanges();

    const field = fixture.nativeElement.querySelector('.expiration-window-field') as HTMLElement;
    const input = field.querySelector('input') as HTMLInputElement;
    const helper = field.querySelector('#expiration-window-hint') as HTMLParagraphElement;

    expect(helper.textContent?.trim()).toBe('Use 0 para mostrar somente lotes vencidos.');
    expect(input.getAttribute('aria-describedby')).toContain('expiration-window-hint');
    expect(fixture.nativeElement.querySelector('#expiration-heading')?.textContent?.trim()).toBe('Validade');
    expect(fixture.nativeElement.textContent).toContain('0 alerta(s) em 01/09/2026.');
    expect(fixture.nativeElement.querySelector('mat-hint')).toBeNull();
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
    expect(fixture.nativeElement.querySelector('#expiration-window-hint')).toBeNull();
    expect((fixture.nativeElement.querySelector('.expiration-window-field input') as HTMLInputElement).getAttribute('aria-describedby')).toBeTruthy();
  });

  it('should present empty alert sets as a valid state and keep zero available quantity on the receipt path', () => {
    lowStockResponse.next({ ...lowStock, alerts: [] });
    expirationResponse.next({ ...expiration, alerts: [] });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Nenhum alerta de estoque baixo');
    expect(fixture.nativeElement.textContent).toContain('Nenhum alerta de validade');

    const refreshedLowStock = new Subject<LowStockAlertsDto>();
    getLowStockAlerts.mockReturnValue(refreshedLowStock);
    fixture.nativeElement.querySelector('.panel button')?.click();
    refreshedLowStock.next({
      ...lowStock,
      alerts: [{ ...lowStock.alerts[0], availableQuantity: '0' }],
    });
    fixture.detectChanges();

    expect(link('Registrar entrada para Essência de lavanda').getAttribute('href')).toBe(
      `/receipts?inventoryItemId=${inventoryItemId}`,
    );
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

  function link(ariaLabel: string): HTMLAnchorElement {
    return fixture.nativeElement.querySelector(`a[aria-label="${ariaLabel}"]`) as HTMLAnchorElement;
  }
});
