import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, ParamMap, convertToParamMap, provideRouter, Router } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';

import { InventoryItemDto, UpdateInventoryItemRequest } from '../../data-access/inventory-item.dto';
import { InventoryItemApiService } from '../../data-access/inventory-item-api.service';
import { InventoryItemEditPage } from './inventory-item-edit-page';

describe('InventoryItemEditPage', () => {
  const inventoryItemId = 'bd194732-51cf-4f73-bc5d-3a9f9337adcc';
  const item: InventoryItemDto = {
    id: inventoryItemId,
    name: 'Lavender Essence',
    description: 'Floral raw material',
    category: 'ESSENCE',
    unitOfMeasure: 'MILLILITER',
    active: true,
    essenceReference: '027',
    productionTypeCode: 'BDS',
    gender: 'F',
  };

  let fixture: ComponentFixture<InventoryItemEditPage>;
  let response: Subject<InventoryItemDto>;
  let pendingUpdate: Subject<InventoryItemDto>;
  let routeParams: Subject<ParamMap>;
  let update: ReturnType<
    typeof vi.fn<(id: string, request: UpdateInventoryItemRequest) => Observable<InventoryItemDto>>
  >;
  let getById: ReturnType<typeof vi.fn<(id: string) => Observable<InventoryItemDto>>>;
  let router: Router;

  beforeEach(async () => {
    response = new Subject<InventoryItemDto>();
    pendingUpdate = new Subject<InventoryItemDto>();
    routeParams = new Subject<ParamMap>();
    update = vi.fn(() => of(item));
    getById = vi.fn(() => response);

    await TestBed.configureTestingModule({
      imports: [InventoryItemEditPage],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap: routeParams } },
        { provide: InventoryItemApiService, useValue: { getById, update } },
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    fixture = TestBed.createComponent(InventoryItemEditPage);
    fixture.detectChanges();
    routeParams.next(convertToParamMap({ inventoryItemId }));
    fixture.detectChanges();
  });

  it('should load and prefill the item while keeping catalog context read-only', () => {
    response.next(item);
    fixture.detectChanges();

    expect(fixture.componentInstance.editModel()).toEqual({
      name: item.name,
      description: item.description,
      active: true,
      essenceReference: item.essenceReference,
      productionTypeCode: item.productionTypeCode,
    });
    expect(fixture.nativeElement.textContent).toContain('Essência');
    expect(fixture.nativeElement.textContent).toContain('Feminino');
    expect(fixture.nativeElement.querySelectorAll('input[readonly]').length).toBe(2);
    expect(fixture.nativeElement.textContent).not.toContain('Categoria é obrigatória.');
  });

  it('should submit allowed fields once and navigate to detail after success', () => {
    response.next(item);
    fixture.detectChanges();
    fixture.componentInstance.editModel.update((model) => ({
      ...model,
      name: 'Lavanda Premium',
      description: 'Updated',
      active: false,
    }));
    fixture.detectChanges();

    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    submitForm();

    expect(update).toHaveBeenCalledWith(inventoryItemId, {
      name: 'Lavanda Premium',
      description: 'Updated',
      active: false,
      essenceReference: '027',
      productionTypeCode: 'BDS',
    });
    expect(navigate).toHaveBeenCalledWith(['/catalog', inventoryItemId]);
  });

  it('should allow stable metadata assignment when the loaded item has none', () => {
    response.next({ ...item, essenceReference: null, productionTypeCode: null });
    fixture.detectChanges();
    fixture.componentInstance.editModel.update((model) => ({
      ...model,
      essenceReference: '014',
      productionTypeCode: 'ESS',
    }));
    fixture.detectChanges();
    vi.spyOn(router, 'navigate').mockResolvedValue(true);

    submitForm();

    expect(update).toHaveBeenCalledWith(inventoryItemId, expect.objectContaining({
      essenceReference: '014',
      productionTypeCode: 'ESS',
    }));
    expect(fixture.nativeElement.textContent).toContain('Referência da essência');
  });

  it('should not offer essence-reference assignment for an ineligible category', () => {
    response.next({
      ...item,
      category: 'BOTTLE',
      unitOfMeasure: 'UNIT',
      essenceReference: null,
      productionTypeCode: null,
      gender: null,
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('Referência da essência');
  });

  it('should prevent duplicate submissions while saving', () => {
    response.next(item);
    fixture.detectChanges();
    update.mockReturnValue(pendingUpdate);
    submitForm();
    submitForm();

    expect(update).toHaveBeenCalledTimes(1);
    expect((fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement).disabled)
      .toBe(true);
  });

  it('should show a localized business error without changing the loaded item', () => {
    response.next(item);
    fixture.detectChanges();
    update.mockReturnValueOnce(
      throwError(() =>
        new HttpErrorResponse({
          status: 422,
          error: {
            timestamp: '2026-09-01T12:00:00Z',
            status: 422,
            error: 'Unprocessable Content',
            code: 'INVENTORY_ITEM_STABLE_METADATA_IMMUTABLE',
            message: 'Assigned catalog metadata cannot be changed or removed',
            path: `/api/v1/inventory-items/${inventoryItemId}`,
            details: { essenceReference: 'Assigned value can only be submitted unchanged' },
          },
        }),
      ),
    );
    submitForm();

    expect(fixture.nativeElement.textContent).toContain(
      'As referências estáveis já atribuídas não podem ser alteradas ou removidas.',
    );
    expect(fixture.componentInstance.editModel().name).toBe(item.name);
  });

  it('should show a localized field validation error after save failure', () => {
    response.next(item);
    fixture.detectChanges();
    update.mockReturnValueOnce(
      throwError(() =>
        new HttpErrorResponse({
          status: 400,
          error: {
            timestamp: '2026-09-01T12:00:00Z',
            status: 400,
            error: 'Bad Request',
            code: 'VALIDATION_ERROR',
            message: 'Request validation failed',
            path: `/api/v1/inventory-items/${inventoryItemId}`,
            details: { name: 'must not be blank' },
          },
        }),
      ),
    );

    submitForm();

    expect(fixture.nativeElement.textContent).toContain('Verifique o valor informado.');
  });

  it('should keep the form available after an infrastructure save failure', () => {
    response.next(item);
    fixture.detectChanges();
    update.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));

    submitForm();

    expect(fixture.nativeElement.textContent).toContain('Ocorreu um erro no servidor. Tente novamente.');
    expect((fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement).disabled)
      .toBe(false);
    expect(fixture.componentInstance.editModel().name).toBe(item.name);
  });

  it('should show load failure and retry the item request', () => {
    response.error(
      new HttpErrorResponse({
        status: 404,
        error: {
          timestamp: '2026-09-01T12:00:00Z',
          status: 404,
          error: 'Not Found',
          code: 'INVENTORY_ITEM_NOT_FOUND',
          message: 'Inventory item not found',
          path: `/api/v1/inventory-items/${inventoryItemId}`,
        },
      }),
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Item de estoque não encontrado.');
    getById.mockReturnValueOnce(of(item));
    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(getById).toHaveBeenCalledTimes(2);
    expect(fixture.componentInstance.editModel().name).toBe('Lavender Essence');
  });

  function submitForm(): void {
    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new SubmitEvent('submit', { bubbles: true, cancelable: true }));
    fixture.detectChanges();
  }
});
