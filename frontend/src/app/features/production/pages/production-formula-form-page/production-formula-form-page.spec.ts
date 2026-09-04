import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { Observable, Subject, of } from 'rxjs';

import { InventoryItemDto } from '../../../catalog/data-access/inventory-item.dto';
import { InventoryItemApiService } from '../../../catalog/data-access/inventory-item-api.service';
import { ProductionFormulaDto, UpsertProductionFormulaRequest } from '../../data-access/production-formula.dto';
import { ProductionFormulaApiService } from '../../data-access/production-formula-api.service';
import { ProductionFormulaFormPage } from './production-formula-form-page';

describe('ProductionFormulaFormPage', () => {
  const items: readonly InventoryItemDto[] = [
    item('output', 'Produto'),
    item('ingredient-a', 'Essência A'),
    item('ingredient-b', 'Essência B', false),
  ];
  const formula: ProductionFormulaDto = {
    id: 'formula-1',
    outputInventoryItemId: 'output',
    outputQuantity: 12.5,
    outputUnitOfMeasure: 'MILLILITER',
    ingredients: [
      { inventoryItemId: 'ingredient-a', quantity: 1.25, unitOfMeasure: 'MILLILITER' },
      { inventoryItemId: 'ingredient-b', quantity: 2, unitOfMeasure: 'MILLILITER' },
    ],
  };

  let fixture: ComponentFixture<ProductionFormulaFormPage>;
  let createResponse: Subject<ProductionFormulaDto>;
  let updateResponse: Subject<ProductionFormulaDto>;
  let create: ReturnType<typeof vi.fn<(request: UpsertProductionFormulaRequest) => Observable<ProductionFormulaDto>>>;
  let update: ReturnType<typeof vi.fn<(id: string, request: UpsertProductionFormulaRequest) => Observable<ProductionFormulaDto>>>;
  let getById: ReturnType<typeof vi.fn<(id: string) => Observable<ProductionFormulaDto>>>;
  let getInventoryItemById: ReturnType<typeof vi.fn<(id: string) => Observable<InventoryItemDto>>>;
  let router: Router;

  async function configure(formulaId: string | null): Promise<void> {
    createResponse = new Subject<ProductionFormulaDto>();
    updateResponse = new Subject<ProductionFormulaDto>();
    create = vi.fn(() => createResponse);
    update = vi.fn(() => updateResponse);
    getById = vi.fn(() => of(formula));
    getInventoryItemById = vi.fn((inventoryItemId) => of(items.find((item) => item.id === inventoryItemId) as InventoryItemDto));

    await TestBed.configureTestingModule({
      imports: [ProductionFormulaFormPage],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap(formulaId === null ? {} : { formulaId })) } },
        { provide: InventoryItemApiService, useValue: { search: vi.fn(() => of(page([]))), getById: getInventoryItemById } },
        { provide: ProductionFormulaApiService, useValue: { create, update, getById } },
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    fixture = TestBed.createComponent(ProductionFormulaFormPage);
    fixture.detectChanges();
  }

  it('should create a formula with multiple ingredient quantities only after backend confirmation', async () => {
    await configure(null);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    setCreateValues();
    click('Adicionar ingrediente');
    fixture.componentInstance.formulaForm.controls.ingredients.at(1).setValue({
      inventoryItem: items[2], quantity: '2.000001',
    });

    submit();

    expect(create).toHaveBeenCalledWith({
      outputInventoryItemId: 'output',
      outputQuantity: 12.5,
      ingredients: [
        { inventoryItemId: 'ingredient-a', quantity: 1.25 },
        { inventoryItemId: 'ingredient-b', quantity: 2.000001 },
      ],
    });
    expect(navigate).not.toHaveBeenCalled();

    createResponse.next({ ...formula, id: 'created-formula' });
    expect(navigate).toHaveBeenCalledWith(['/production/formulas', 'created-formula']);
  });

  it('should add and remove ingredient rows while retaining one required row', async () => {
    await configure(null);

    click('Adicionar ingrediente');
    expect(fixture.componentInstance.formulaForm.controls.ingredients.length).toBe(2);
    click('Remover ingrediente');
    expect(fixture.componentInstance.formulaForm.controls.ingredients.length).toBe(1);
    expect(button('Remover ingrediente').disabled).toBe(true);
  });

  it('should load and update an existing formula', async () => {
    await configure(formula.id);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    expect(getById).toHaveBeenCalledWith(formula.id);
    expect(getInventoryItemById).toHaveBeenCalledWith('output');
    expect(getInventoryItemById).toHaveBeenCalledWith('ingredient-a');
    expect(getInventoryItemById).toHaveBeenCalledWith('ingredient-b');
    expect(fixture.componentInstance.formulaForm.getRawValue()).toEqual({
      outputInventoryItem: items[0],
      outputQuantity: '12.5',
      ingredients: [
        { inventoryItem: items[1], quantity: '1.25' },
        { inventoryItem: items[2], quantity: '2' },
      ],
    });
    expect(fixture.nativeElement.textContent).toContain('Essência B');

    submit();
    expect(update).toHaveBeenCalledWith(formula.id, {
      outputInventoryItemId: 'output',
      outputQuantity: 12.5,
      ingredients: [
        { inventoryItemId: 'ingredient-a', quantity: 1.25 },
        { inventoryItemId: 'ingredient-b', quantity: 2 },
      ],
    });
    updateResponse.next(formula);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Fórmula atualizada com sucesso.');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('should render mapped backend errors without reporting success', async () => {
    await configure(null);
    setCreateValues();
    submit();

    createResponse.error(new HttpErrorResponse({
      status: 422,
      error: {
        timestamp: '2026-09-03T12:00:00Z', status: 422, error: 'Unprocessable Entity',
        code: 'INVALID_PRODUCTION_FORMULA', message: 'Formula is invalid',
        path: '/api/v1/production/formulas', details: { ingredients: 'invalid' },
      },
    }));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Verifique o valor informado.');
    expect(fixture.nativeElement.textContent).not.toContain('sucesso');
  });

  it('should accept an API-valid decimal that JavaScript cannot represent exactly', async () => {
    await configure(null);
    fixture.componentInstance.formulaForm.setValue({
      outputInventoryItem: items[0], outputQuantity: '9007199254740.000001',
      ingredients: [{ inventoryItem: items[1], quantity: '1' }],
    });

    submit();

    expect(create).toHaveBeenCalled();
  });

  function setCreateValues(): void {
    fixture.componentInstance.formulaForm.setValue({
      outputInventoryItem: items[0], outputQuantity: '12.5',
      ingredients: [{ inventoryItem: items[1], quantity: '1.25' }],
    });
    fixture.detectChanges();
  }

  function submit(): void {
    (fixture.nativeElement.querySelector('form') as HTMLFormElement)
      .dispatchEvent(new SubmitEvent('submit', { bubbles: true, cancelable: true }));
    fixture.detectChanges();
  }

  function click(text: string): void {
    button(text).click();
    fixture.detectChanges();
  }

  function button(text: string): HTMLButtonElement {
    return Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>).find(
      (candidate) => candidate.textContent?.includes(text),
    ) as HTMLButtonElement;
  }
});

function item(id: string, name: string, active = true): InventoryItemDto {
  return {
    id, name, description: null, category: 'ESSENCE', unitOfMeasure: 'MILLILITER', active,
    essenceReference: null, productionTypeCode: null,
  };
}

function page(content: readonly InventoryItemDto[]) {
  return { content, page: 0, size: 100, totalElements: content.length, totalPages: 1 };
}
