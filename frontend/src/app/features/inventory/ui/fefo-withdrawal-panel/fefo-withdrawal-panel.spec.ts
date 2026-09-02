import {HttpErrorResponse} from '@angular/common/http';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {Observable, Subject} from 'rxjs';

import {FefoWithdrawalApiService} from '../../data-access/fefo-withdrawal-api.service';
import {RegisterFefoWithdrawalDto} from '../../data-access/fefo-withdrawal.dto';
import {FefoWithdrawalPanel} from './fefo-withdrawal-panel';

describe('FefoWithdrawalPanel', () => {
  const inventoryItemId = 'bd194732-51cf-4f73-bc5d-3a9f9337adcc';
  const singleAllocation: RegisterFefoWithdrawalDto = {
    inventoryItemId,
    requestedQuantity: 25.5,
    allocatedQuantity: 25.5,
    allocations: [
      {
        batchId: 'b78247ac-5e22-4097-a609-d396c81fab64',
        movementId: '2b459b94-25e0-4fbf-bb6e-8bd2d92446ff',
        quantity: 25.5,
      },
    ],
  };

  let fixture: ComponentFixture<FefoWithdrawalPanel>;
  let response: Subject<RegisterFefoWithdrawalDto>;
  let register: ReturnType<
    typeof vi.fn<(id: string, request: unknown) => Observable<RegisterFefoWithdrawalDto>>
  >;
  let completed: ReturnType<typeof vi.fn<() => void>>;

  beforeEach(async () => {
    response = new Subject<RegisterFefoWithdrawalDto>();
    register = vi.fn(() => response);
    completed = vi.fn();

    await TestBed.configureTestingModule({
      imports: [FefoWithdrawalPanel],
      providers: [{ provide: FefoWithdrawalApiService, useValue: { register } }],
    }).compileComponents();

    fixture = TestBed.createComponent(FefoWithdrawalPanel);
    fixture.componentRef.setInput('inventoryItemId', inventoryItemId);
    fixture.componentRef.setInput('itemName', 'Lavender Essence');
    fixture.componentRef.setInput('unitOfMeasure', 'MILLILITER');
    fixture.componentRef.setInput('availableQuantity', 2);
    fixture.componentRef.setInput('active', true);
    fixture.componentInstance.withdrawalCompleted.subscribe(completed);
    fixture.detectChanges();
  });

  it.each([
    ['', 'Quantidade é obrigatória.'],
    ['0', 'quantidade positiva'], ['-1', 'quantidade positiva'], ['1e3', 'quantidade positiva'],
    ['10000000000000', 'no máximo 13 dígitos inteiros'], ['1.1234567', 'até 6 casas decimais'],
    ['9007199254740.000001', 'não pode ser representada com segurança'],
    ['9999999999999.999999', 'não pode ser representada com segurança'],
  ])('should reject invalid quantity %s before confirmation', (quantity, message) => {
    setForm(quantity, '');

    click('Revisar saída');

    expect(register).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain(message);
  });

  it.each([
    ['0.1', 0.1],
    ['0.2', 0.2],
    ['0.3', 0.3],
    ['0.100000', 0.1],
    ['1.000001', 1.000001],
    ['25.500000', 25.5],
  ])('should accept a JSON-safe decimal quantity %s', (quantity, expectedQuantity) => {
    setForm(quantity, 'Production');

    click('Revisar saída'); click('Confirmar saída');

    expect(register).toHaveBeenCalledWith(inventoryItemId, {
      quantity: expectedQuantity,
      reason: 'Production',
    });
  });

  it('should reject a reason longer than 255 characters', () => {
    setForm('1', 'a'.repeat(256));

    click('Revisar saída');

    expect(register).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Motivo deve ter no máximo 255 caracteres.');
  });

  it('should require explicit confirmation and normalize a whitespace reason to null', () => {
    setForm('1.000001', '   ');

    click('Revisar saída');

    expect(register).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Confirmar saída de estoque');

    click('Confirmar saída');

    expect(register).toHaveBeenCalledWith(inventoryItemId, { quantity: 1.000001, reason: null });
  });

  it('should return from confirmation to editing without posting', () => {
    setForm('25.500000', 'Production');
    click('Revisar saída');

    click('Voltar à edição');

    expect(register).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Revisar saída');
  });

  it('should not present an enabled withdrawal action for a known inactive item', () => {
    fixture.componentRef.setInput('active', false);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('inativo');
    expect(findButton('Revisar saída')).toBeUndefined();
  });

  it('should lock the transaction after an inactive backend response and reset the lock for another item', () => {
    submitValidWithdrawal();

    response.error(
      apiError(422, 'INACTIVE_INVENTORY_ITEM', 'Inventory item is inactive: item-a', undefined),
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Este item de estoque está inativo.');
    expect(findButton('Confirmar saída')).toBeUndefined();
    (fixture.componentInstance as unknown as { confirmWithdrawal(): void }).confirmWithdrawal();
    expect(register).toHaveBeenCalledTimes(1);
    expect(fixture.nativeElement.textContent).not.toContain('Saída registrada');

    fixture.componentRef.setInput('inventoryItemId', '5184d508-35eb-42de-a2a0-44c4f6c9b9ae');
    fixture.componentRef.setInput('itemName', 'Lavender Base');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Lavender Base');
    expect(fixture.nativeElement.textContent).toContain('Revisar saída');
    expect(fixture.nativeElement.textContent).not.toContain('Inventory item is inactive: item-a');
  });

  it('should ignore a stale inactive response after the item context changes', () => {
    submitValidWithdrawal();
    fixture.componentRef.setInput('inventoryItemId', '5184d508-35eb-42de-a2a0-44c4f6c9b9ae');
    fixture.componentRef.setInput('itemName', 'Lavender Base');
    fixture.detectChanges();

    response.error(
      apiError(422, 'INACTIVE_INVENTORY_ITEM', 'Inventory item is inactive: item-a', undefined),
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Lavender Base');
    expect(fixture.nativeElement.textContent).toContain('Revisar saída');
    expect(fixture.nativeElement.textContent).not.toContain('Inventory item is inactive: item-a');
  });

  it('should allow a locally valid quantity above displayed available stock to reach the backend', () => {
    setForm('3', 'Production');
    click('Revisar saída'); click('Confirmar saída');

    expect(register).toHaveBeenCalledWith(inventoryItemId, { quantity: 3, reason: 'Production' });
    expect(fixture.nativeElement.textContent).toContain('Estoque disponível: 2 Mililitro');
    expect(completed).not.toHaveBeenCalled();
  });

  it('should submit exactly once while a request is in flight', () => {
    setForm('25.5', 'Production');
    click('Revisar saída'); click('Confirmar saída');
    (fixture.componentInstance as unknown as { confirmWithdrawal(): void }).confirmWithdrawal();

    expect(register).toHaveBeenCalledTimes(1);
  });

  it('should render one committed allocation and require an explicit new transaction', () => {
    setForm('25.5', 'Production');
    click('Revisar saída'); click('Confirmar saída');
    response.next(singleAllocation);
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(completed).toHaveBeenCalledTimes(1);
    expect(text).toContain('Saída registrada');
    expect(text).toContain(singleAllocation.allocations[0].batchId);
    expect(text).toContain(singleAllocation.allocations[0].movementId);
    expect(findButton('Confirmar saída')).toBeUndefined();

    click('Registrar outra saída');

    expect(fixture.nativeElement.textContent).toContain('Revisar saída');
  });

  it('should render every committed allocation in a multi-batch result', () => {
    setForm('25.5', 'Production');
    click('Revisar saída');
    click('Confirmar saída');
    response.next({
      ...singleAllocation,
      allocations: [
        { ...singleAllocation.allocations[0], quantity: 10 },
        {
          batchId: '5184d508-35eb-42de-a2a0-44c4f6c9b9ae',
          movementId: '12bf0c0d-93db-4a78-bb05-b9e5d0a1e15d',
          quantity: 15.5,
        },
      ],
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('5184d508-35eb-42de-a2a0-44c4f6c9b9ae');
    expect(fixture.nativeElement.textContent).toContain('12bf0c0d-93db-4a78-bb05-b9e5d0a1e15d');
  });

  it.each([
    [400, 'VALIDATION_ERROR', 'Withdrawal data is invalid.', {}],
    [404, 'INVENTORY_ITEM_NOT_FOUND', 'Inventory item not found: 123', undefined],
    [422, 'INACTIVE_INVENTORY_ITEM', 'Inventory item is inactive: 123', undefined],
  ])('should surface backend status %i feedback', (status, code, message, details) => {
    submitValidWithdrawal();

    response.error(apiError(status, code, message, details));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(code === 'VALIDATION_ERROR' ? 'Revise os dados informados.' : code === 'INVENTORY_ITEM_NOT_FOUND' ? 'Item de estoque não encontrado.' : 'Este item de estoque está inativo.');
    expect(fixture.nativeElement.textContent).not.toContain('Saída registrada');
  });

  it('should return backend validation field errors to the editable controls', () => {
    submitValidWithdrawal();

    response.error(apiError(400, 'VALIDATION_ERROR', 'Request validation failed', { quantity: 'must be positive' }));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Revisar saída');
    expect(fixture.nativeElement.textContent).toContain('Verifique o valor informado.');
    expect(findButton('Confirmar saída')).toBeUndefined();
  });

  it('should show insufficient eligible stock details without changing the requested quantity', () => {
    setForm('80', 'Production');
    click('Revisar saída');
    click('Confirmar saída');

    response.error(
      apiError(422, 'INSUFFICIENT_ELIGIBLE_STOCK', 'Insufficient eligible stock for inventory item 123', {
        availableQuantity: '55.000000',
      }),
    );
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Nenhuma saída parcial foi registrada.');
    expect(text).toContain('55 Mililitro');
    expect(text).toContain('80 Mililitro');
    expect(text).not.toContain('Saída registrada');
  });

  function setForm(quantity: string, reason: string): void {
    fixture.componentInstance.withdrawalModel.set({ quantity, reason });
    fixture.detectChanges();
  }

  function submitValidWithdrawal(): void {
    setForm('25.5', 'Production');
    click('Revisar saída'); click('Confirmar saída');
  }

  function click(label: string): void {
    const button = findButton(label);
    expect(button).toBeDefined();
    button?.click();
    fixture.detectChanges();
  }

  function findButton(label: string): HTMLButtonElement | undefined {
    return Array.from(fixture.nativeElement.querySelectorAll('button')).find(
      (candidate) => (candidate as HTMLButtonElement).textContent?.includes(label),
    ) as HTMLButtonElement | undefined;
  }

  function apiError(
    status: number,
    code: string,
    message: string,
    details: Record<string, string> | undefined,
  ): HttpErrorResponse {
    return new HttpErrorResponse({
      status,
      error: {
        timestamp: '2026-09-01T20:30:00Z',
        status,
        error: status === 400 ? 'Bad Request' : status === 404 ? 'Not Found' : 'Unprocessable Content',
        code,
        message,
        path: `/api/v1/inventory/items/${inventoryItemId}/withdrawals`,
        ...(details === undefined ? {} : { details }),
      },
    });
  }
});
