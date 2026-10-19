import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { OrderApiService } from '../data-access/order-api.service';
import { OrderDto, SaleStockCorrectionDto } from '../data-access/order.dto';
import { order } from '../testing/order-fixture';
import { SaleStockCorrectionForm } from './sale-stock-correction-form';

const sale: OrderDto = {
  ...order,
  status: 'CONFIRMED',
  lines: [
    {
      ...order.lines[0]!,
      allocations: [
        { batchId: 'first-batch', quantity: '1.000001', movementId: 'first-consumption' },
        { batchId: 'second-batch', quantity: '2.000000', movementId: 'second-consumption' },
      ],
    },
  ],
};
const correction: SaleStockCorrectionDto = {
  id: 'correction',
  saleId: sale.id,
  lineId: sale.lines[0]!.id,
  batchId: 'second-batch',
  quantity: '0.000001',
  reason: 'Inspected return',
  inspected: true,
  accepted: true,
  actor: 'operator',
  occurredAt: order.updatedAt,
  movementId: 'return-movement',
};

async function setup(api: Partial<OrderApiService> = {}) {
  const correctStock = vi.fn<OrderApiService['correctStock']>(() => of(correction));
  const stockCorrections = vi.fn(() => of([]));
  await TestBed.configureTestingModule({
    imports: [SaleStockCorrectionForm],
    providers: [{ provide: OrderApiService, useValue: { correctStock, stockCorrections, ...api } }],
  }).compileComponents();
  const fixture = TestBed.createComponent(SaleStockCorrectionForm);
  fixture.componentRef.setInput('sale', sale);
  fixture.detectChanges();
  await fixture.whenStable();
  const component = fixture.componentInstance;
  const fill = () =>
    component.form.setValue({
      allocation: `${sale.lines[0]!.id}/second-batch`,
      quantity: '0,000001',
      reason: ' Inspected return ',
      inspected: true,
      accepted: true,
    });
  const submit = () => {
    fixture.nativeElement
      .querySelector('form')
      .dispatchEvent(new Event('submit', { cancelable: true }));
    fixture.detectChanges();
  };
  return { fixture, component, fill, submit, correctStock, stockCorrections };
}

describe('SaleStockCorrectionForm', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('uses native randomUUID when available', async () => {
    const id = '12345678-1234-4234-8234-123456789abc';
    const randomUUID = vi.fn(() => id);
    const getRandomValues = vi.fn();
    vi.stubGlobal('crypto', { randomUUID, getRandomValues });
    const { fill, submit, correctStock } = await setup();
    fill();
    submit();
    expect(correctStock).toHaveBeenCalledWith(sale.id, id, expect.any(Object));
    expect(randomUUID).toHaveBeenCalledTimes(1);
    expect(getRandomValues).not.toHaveBeenCalled();
  });

  it('submits successfully with an RFC 4122 v4 UUID when only getRandomValues is available', async () => {
    const getRandomValues = vi.fn((bytes: Uint8Array) => bytes.fill(0xff));
    vi.stubGlobal('crypto', { getRandomValues });
    const { fixture, fill, submit, correctStock } = await setup();
    fill();
    submit();
    // All-one input proves that both version and variant bits are overwritten correctly.
    const id = 'ffffffff-ffff-4fff-bfff-ffffffffffff';
    expect(correctStock).toHaveBeenCalledExactlyOnceWith(sale.id, id, expect.any(Object));
    expect(correctStock.mock.calls[0]?.[1]).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/,
    );
    expect(getRandomValues).toHaveBeenCalledExactlyOnceWith(expect.any(Uint8Array));
    expect(fixture.nativeElement.textContent).toContain('Devolução registrada');
  });

  it('retains the fallback UUID across failed responses and pending duplicates and renews it for a new correction', async () => {
    let seed = 0;
    const getRandomValues = vi.fn((bytes: Uint8Array) => bytes.fill(seed++));
    vi.stubGlobal('crypto', { getRandomValues });
    const firstResponse = new Subject<SaleStockCorrectionDto>();
    const retryResponse = new Subject<SaleStockCorrectionDto>();
    const newResponse = new Subject<SaleStockCorrectionDto>();
    const correctStock = vi
      .fn<OrderApiService['correctStock']>()
      .mockReturnValueOnce(firstResponse.asObservable())
      .mockReturnValueOnce(retryResponse.asObservable())
      .mockReturnValueOnce(newResponse.asObservable());
    const { fixture, component, fill, submit } = await setup({ correctStock });
    fill();
    submit();
    submit();
    const firstId = '00000000-0000-4000-8000-000000000000';
    expect(correctStock).toHaveBeenCalledExactlyOnceWith(sale.id, firstId, expect.any(Object));
    expect(getRandomValues).toHaveBeenCalledTimes(1);
    expect(component.form.disabled).toBe(true);
    firstResponse.error(new HttpErrorResponse({ status: 0 }));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Tentar registrar novamente');
    submit();
    submit();
    expect(correctStock).toHaveBeenCalledTimes(2);
    expect(correctStock.mock.calls[1]).toEqual(correctStock.mock.calls[0]);
    expect(getRandomValues).toHaveBeenCalledTimes(1);
    retryResponse.next({ ...correction, id: firstId });
    retryResponse.complete();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain(firstId);
    fill();
    submit();
    const newId = '01010101-0101-4101-8101-010101010101';
    expect(correctStock).toHaveBeenNthCalledWith(3, sale.id, newId, expect.any(Object));
    expect(newId).not.toBe(firstId);
    expect(getRandomValues).toHaveBeenCalledTimes(2);
    newResponse.next({ ...correction, id: newId });
    newResponse.complete();
  });

  it.each(['missing crypto', 'missing methods', 'getRandomValues failure', 'randomUUID failure'])(
    'shows a retryable error and sends no correction request on %s',
    async (failure) => {
      const fail = () => {
        throw new Error('Randomness unavailable');
      };
      vi.stubGlobal(
        'crypto',
        failure === 'missing crypto'
          ? undefined
          : failure === 'missing methods'
            ? {}
            : failure === 'getRandomValues failure'
              ? { getRandomValues: fail }
              : { randomUUID: fail },
      );
      const { fixture, component, fill, submit, correctStock } = await setup();
      fill();
      submit();
      expect(correctStock).not.toHaveBeenCalled();
      expect(fixture.nativeElement.textContent).toContain('Nenhuma devolução foi enviada');
      expect(fixture.nativeElement.textContent).not.toContain(
        'A tentativa mantém o mesmo identificador',
      );
      expect(component.form.enabled).toBe(true);
      expect(
        (fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement)
          .disabled,
      ).toBe(false);
      vi.stubGlobal('crypto', { getRandomValues: (bytes: Uint8Array) => bytes.fill(0) });
      submit();
      expect(correctStock).toHaveBeenCalledExactlyOnceWith(
        sale.id,
        '00000000-0000-4000-8000-000000000000',
        expect.any(Object),
      );
    },
  );

  it('requires allocated batch, exact positive quantity, bounded reason and both explicit decisions', async () => {
    const { fixture, component, fill, submit, correctStock } = await setup();
    submit();
    expect(correctStock).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Confirme explicitamente');
    fill();
    for (const quantity of ['0', '-1', '0.0000001', '10000000000000', 'invalid']) {
      component.form.controls.quantity.setValue(quantity);
      submit();
    }
    fill();
    component.form.controls.reason.setValue(' ');
    submit();
    component.form.controls.reason.setValue('x'.repeat(256));
    submit();
    fill();
    component.form.controls.inspected.setValue(false);
    submit();
    fill();
    component.form.controls.accepted.setValue(false);
    submit();
    fill();
    component.form.controls.allocation.setValue('');
    submit();
    expect(correctStock).not.toHaveBeenCalled();
  });

  it('renders original batch choices, sends exact values and blocks duplicate requests while pending', async () => {
    const response = new Subject<SaleStockCorrectionDto>();
    const correctStock = vi.fn<OrderApiService['correctStock']>(() => response.asObservable());
    const { fixture, component, fill, submit } = await setup({ correctStock });
    const select = fixture.nativeElement.querySelector('mat-select') as HTMLElement;
    select.click();
    fixture.detectChanges();
    await fixture.whenStable();
    const options = Array.from(document.querySelectorAll<HTMLElement>('mat-option'));
    expect(options.map((o) => o.textContent).join(' ')).toContain('first-batch');
    expect(options.map((o) => o.textContent).join(' ')).toContain('second-batch');
    options.find((o) => o.textContent?.includes('second-batch'))!.click();
    fixture.detectChanges();
    expect(component.form.controls.allocation.value).toBe(`${sale.lines[0]!.id}/second-batch`);
    fill();
    submit();
    submit();
    expect(correctStock).toHaveBeenCalledTimes(1);
    expect(correctStock.mock.calls[0]).toEqual([
      sale.id,
      expect.any(String),
      {
        lineId: sale.lines[0]!.id,
        batchId: 'second-batch',
        quantity: '0.000001',
        reason: 'Inspected return',
        inspected: true,
        accepted: true,
      },
    ]);
    expect(component.form.disabled).toBe(true);
    expect(
      (fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement).disabled,
    ).toBe(true);
    response.next(correction);
    response.complete();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Devolução registrada');
    expect(fixture.nativeElement.textContent).toContain('return-movement');
    expect(component.form.enabled).toBe(true);
    expect(component.form.controls.accepted.value).toBe(false);
    expect(component.form.controls.inspected.value).toBe(false);
    fill();
    submit();
    expect(correctStock.mock.calls[1]?.[1]).not.toBe(correctStock.mock.calls[0]?.[1]);
  });

  it('keeps immutable request identity and payload after lost response and retries only explicitly', async () => {
    const correctStock = vi
      .fn()
      .mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 0 })))
      .mockReturnValueOnce(of(correction));
    const { fixture, component, fill, submit } = await setup({ correctStock });
    fill();
    submit();
    expect(correctStock).toHaveBeenCalledTimes(1);
    expect(component.form.disabled).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Não foi possível conectar');
    expect(fixture.nativeElement.textContent).toContain('Tentar registrar novamente');
    // Programmatic edits cannot change an ambiguous in-flight identity's payload.
    component.form.controls.quantity.setValue('9');
    submit();
    expect(correctStock.mock.calls[1]).toEqual(correctStock.mock.calls[0]);
    expect(fixture.nativeElement.textContent).toContain('return-movement');
  });

  it.each(['SALE_RETURN_INELIGIBLE', 'SALE_CORRECTION_LIMIT_EXCEEDED'])(
    'shows backend eligibility failure %s and permits revising definitively rejected data',
    async (code) => {
      const correctStock = vi.fn(() =>
        throwError(
          () =>
            new HttpErrorResponse({
              status: code === 'SALE_RETURN_INELIGIBLE' ? 422 : 409,
              error: {
                code,
                timestamp: order.updatedAt,
                status: code === 'SALE_RETURN_INELIGIBLE' ? 422 : 409,
                error: 'Business error',
                message: 'Rejected',
                path: '/api/v1/sales/sale/stock-corrections/id',
              },
            }),
        ),
      );
      const { fixture, component, fill, submit } = await setup({ correctStock });
      fill();
      submit();
      expect(fixture.nativeElement.textContent).not.toContain('Devolução registrada');
      const revise = Array.from(fixture.nativeElement.querySelectorAll('button')).find((b) =>
        (b as HTMLButtonElement).textContent?.includes('Revisar dados'),
      ) as HTMLButtonElement;
      revise.click();
      fixture.detectChanges();
      expect(component.form.enabled).toBe(true);
      expect(component.form.controls.reason.value).toBe(' Inspected return ');
    },
  );

  it('loads persisted audit on reopen and renders explicit empty/error/history retry states', async () => {
    const stockCorrections = vi
      .fn()
      .mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })))
      .mockReturnValueOnce(of([correction]));
    const { fixture } = await setup({ stockCorrections });
    expect(fixture.nativeElement.textContent).toContain('Recarregar devoluções');
    const retry = Array.from(fixture.nativeElement.querySelectorAll('button')).find((b) =>
      (b as HTMLButtonElement).textContent?.includes('Recarregar'),
    ) as HTMLButtonElement;
    retry.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent;
    for (const expected of [
      'return-movement',
      'operator',
      'Inspected return',
      'second-batch',
      'correction',
    ])
      expect(text).toContain(expected);
    expect(stockCorrections).toHaveBeenCalledTimes(2);
  });

  it('shows pending and empty history without calculating eligibility locally', async () => {
    const response = new Subject<readonly SaleStockCorrectionDto[]>();
    const { fixture } = await setup({ stockCorrections: () => response.asObservable() });
    expect(fixture.nativeElement.textContent).toContain('Carregando devoluções');
    response.next([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Nenhuma devolução aceita');
  });
});
