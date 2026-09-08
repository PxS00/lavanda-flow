import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { Observable, Subject } from 'rxjs';

import { BatchInventoryEntryDto } from '../../data-access/inventory-operations.dto';
import { StockMaintenanceApiService } from '../../data-access/stock-maintenance-api.service';
import {
  StockMaintenanceMovementDto,
  StockMaintenanceRequest,
} from '../../data-access/stock-maintenance.dto';
import { StockMaintenanceDialog } from './stock-maintenance-dialog';

describe('StockMaintenanceDialog', () => {
  const batch: BatchInventoryEntryDto = {
    batchId: 'b78247ac-5e22-4097-a609-d396c81fab64',
    inventoryItemId: 'bd194732-51cf-4f73-bc5d-3a9f9337adcc',
    supplierId: 'supplier-1',
    lotCode: 'LOT-A',
    initialQuantity: '100',
    currentQuantity: '40.5',
    receivedAt: '2026-08-01',
    expiresAt: '2026-12-01',
    status: 'AVAILABLE',
  };
  const movement: StockMaintenanceMovementDto = {
    movementId: '2b459b94-25e0-4fbf-bb6e-8bd2d92446ff',
    batchId: batch.batchId,
    type: 'ADJUSTMENT_IN',
    quantity: '1',
    resultingBalance: '41.5',
    reason: 'Contagem física',
    occurredAt: '2026-09-08T15:00:00Z',
  };

  let fixture: ComponentFixture<StockMaintenanceDialog>;
  let adjustmentResponse: Subject<StockMaintenanceMovementDto>;
  let lossResponse: Subject<StockMaintenanceMovementDto>;
  let disposalResponse: Subject<StockMaintenanceMovementDto>;
  let adjust: ReturnType<
    typeof vi.fn<
      (id: string, request: StockMaintenanceRequest) => Observable<StockMaintenanceMovementDto>
    >
  >;
  let registerLoss: ReturnType<
    typeof vi.fn<
      (id: string, request: StockMaintenanceRequest) => Observable<StockMaintenanceMovementDto>
    >
  >;
  let registerExpiredDisposal: ReturnType<
    typeof vi.fn<
      (id: string, request: StockMaintenanceRequest) => Observable<StockMaintenanceMovementDto>
    >
  >;
  let close: ReturnType<typeof vi.fn<(result?: StockMaintenanceMovementDto) => void>>;

  beforeEach(async () => {
    adjustmentResponse = new Subject<StockMaintenanceMovementDto>();
    lossResponse = new Subject<StockMaintenanceMovementDto>();
    disposalResponse = new Subject<StockMaintenanceMovementDto>();
    adjust = vi.fn(() => adjustmentResponse);
    registerLoss = vi.fn(() => lossResponse);
    registerExpiredDisposal = vi.fn(() => disposalResponse);
    close = vi.fn();

    await TestBed.configureTestingModule({
      imports: [StockMaintenanceDialog],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: batch },
        { provide: MatDialogRef, useValue: { close } },
        {
          provide: StockMaintenanceApiService,
          useValue: { adjust, registerLoss, registerExpiredDisposal },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(StockMaintenanceDialog);
    fixture.detectChanges();
  });

  it('should display the selected batch and offer only the approved operations', () => {
    const text = fixture.nativeElement.textContent as string;

    expect(text).toContain('LOT-A');
    expect(text).toContain('40,5');
    expect(text).toContain('Disponível');
    expect(text).toContain('01/12/2026');
    expect(
      Array.from(
        fixture.nativeElement.querySelectorAll(
          'input[type="radio"]',
        ) as NodeListOf<HTMLInputElement>,
      ).map((input) => input.value),
    ).toEqual(['ADJUSTMENT', 'LOSS', 'EXPIRED_DISPOSAL']);
  });

  it('should send a positive adjustment with its exact decimal string without confirmation', () => {
    setForm('ADJUSTMENT', '9999999999999.123456', ' Contagem física ');

    click('Registrar manutenção');

    expect(adjust).toHaveBeenCalledWith(batch.batchId, {
      quantity: '9999999999999.123456',
      reason: 'Contagem física',
    });
    expect(fixture.nativeElement.textContent).not.toContain('Confirmar redução de estoque');
  });

  it('should require confirmation before a negative adjustment', () => {
    setForm('ADJUSTMENT', '-2.500000', 'Contagem física');

    click('Registrar manutenção');

    expect(adjust).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Confirmar redução de estoque');
    expect(fixture.nativeElement.textContent).toContain('em 2,5');
    expect(fixture.nativeElement.textContent).not.toContain('em -2,5');
    click('Confirmar');
    expect(adjust).toHaveBeenCalledWith(batch.batchId, {
      quantity: '-2.500000',
      reason: 'Contagem física',
    });
  });

  it.each([
    ['LOSS', 'registerLoss'],
    ['EXPIRED_DISPOSAL', 'registerExpiredDisposal'],
  ] as const)('should require confirmation before %s', (operation, method) => {
    setForm(operation, '2.5', 'Motivo');

    click('Registrar manutenção');

    expect(fixture.nativeElement.textContent).toContain('Confirmar redução de estoque');
    expect(
      method === 'registerLoss' ? registerLoss : registerExpiredDisposal,
    ).not.toHaveBeenCalled();
    click('Confirmar');
    expect(method === 'registerLoss' ? registerLoss : registerExpiredDisposal).toHaveBeenCalledWith(
      batch.batchId,
      { quantity: '2.5', reason: 'Motivo' },
    );
  });

  it.each(['0', '-0.000000', '1e3', '10000000000000', '1.1234567'])(
    'should reject invalid adjustment quantity %s',
    (quantity) => {
      setForm('ADJUSTMENT', quantity, 'Motivo');

      click('Registrar manutenção');

      expect(adjust).not.toHaveBeenCalled();
      expect(fixture.nativeElement.textContent).toContain('Use um ajuste diferente de zero');
    },
  );

  it('should reject blank and over-length reasons locally', () => {
    setForm('LOSS', '1', '   ');
    click('Registrar manutenção');
    expect(registerLoss).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Motivo é obrigatório.');

    setForm('LOSS', '1', 'a'.repeat(256));
    click('Registrar manutenção');
    expect(registerLoss).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain(
      'Motivo deve ter no máximo 255 caracteres.',
    );
  });

  it('should submit exactly once while a confirmation request is pending', () => {
    setForm('LOSS', '1', 'Motivo');
    click('Registrar manutenção');
    click('Confirmar');
    (fixture.componentInstance as unknown as { confirm(): void }).confirm();

    expect(registerLoss).toHaveBeenCalledTimes(1);
  });

  it('should close only after the backend confirms the movement', () => {
    setForm('ADJUSTMENT', '1', 'Motivo');
    click('Registrar manutenção');
    expect(close).not.toHaveBeenCalled();

    adjustmentResponse.next(movement);
    expect(close).toHaveBeenCalledWith(movement);
  });

  it('should leave expired-disposal eligibility to the backend and show its feedback', () => {
    setForm('EXPIRED_DISPOSAL', '1', 'Motivo');
    click('Registrar manutenção');
    click('Confirmar');
    disposalResponse.error(apiError(422, 'BATCH_NOT_EXPIRED'));
    fixture.detectChanges();

    expect(registerExpiredDisposal).toHaveBeenCalledWith(batch.batchId, {
      quantity: '1',
      reason: 'Motivo',
    });
    expect(close).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Este lote ainda não está vencido');
  });

  it.each([
    [400, 'VALIDATION_ERROR', 'Revise os dados informados.'],
    [404, 'BATCH_NOT_FOUND', 'Lote não encontrado.'],
    [422, 'INSUFFICIENT_STOCK', 'Um dos lotes selecionados não possui saldo suficiente.'],
    [0, 'NETWORK', 'Não foi possível conectar ao servidor.'],
    [500, 'INTERNAL_ERROR', 'Ocorreu um erro no servidor. Tente novamente.'],
  ])(
    'should present actionable backend failure feedback for %s',
    (status, code, expectedMessage) => {
      setForm('ADJUSTMENT', '1', 'Motivo');
      click('Registrar manutenção');
      adjustmentResponse.error(
        status === 0 ? new HttpErrorResponse({ status: 0 }) : apiError(status, code),
      );
      fixture.detectChanges();

      expect(close).not.toHaveBeenCalled();
      expect(fixture.nativeElement.textContent).toContain(expectedMessage);
    },
  );

  function setForm(
    operation: 'ADJUSTMENT' | 'LOSS' | 'EXPIRED_DISPOSAL',
    quantity: string,
    reason: string,
  ): void {
    fixture.componentInstance.maintenanceForm.setValue({ operation, quantity, reason });
    fixture.detectChanges();
  }

  function click(label: string): void {
    const button = Array.from(fixture.nativeElement.querySelectorAll('button')).find(
      (candidate) => (candidate as HTMLButtonElement).textContent?.trim() === label,
    ) as HTMLButtonElement | undefined;
    expect(button).toBeDefined();
    button?.click();
    fixture.detectChanges();
  }

  function apiError(status: number, code: string): HttpErrorResponse {
    return new HttpErrorResponse({
      status,
      error: {
        timestamp: '2026-09-08T15:00:00Z',
        status,
        error: 'Error',
        code,
        message: 'Backend message',
        path: `/api/v1/inventory/batches/${batch.batchId}`,
      },
    });
  }
});
