import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, Subject } from 'rxjs';

import { ProductionFormulaDto } from '../../data-access/production-formula.dto';
import { ProductionFormulaApiService } from '../../data-access/production-formula-api.service';
import { ProductionFormulaListPage } from './production-formula-list-page';

describe('ProductionFormulaListPage', () => {
  const formula: ProductionFormulaDto = {
    id: 'formula-1',
    outputInventoryItemId: 'output-item',
    outputQuantity: 10.5,
    outputUnitOfMeasure: 'MILLILITER',
    ingredients: [
      { inventoryItemId: 'ingredient-a', quantity: 2.5, unitOfMeasure: 'MILLILITER' },
      { inventoryItemId: 'ingredient-b', quantity: 1, unitOfMeasure: 'GRAM' },
    ],
  };

  let fixture: ComponentFixture<ProductionFormulaListPage>;
  let response: Subject<readonly ProductionFormulaDto[]>;
  let list: ReturnType<typeof vi.fn<() => Observable<readonly ProductionFormulaDto[]>>>;

  beforeEach(async () => {
    response = new Subject<readonly ProductionFormulaDto[]>();
    list = vi.fn(() => response);

    await TestBed.configureTestingModule({
      imports: [ProductionFormulaListPage],
      providers: [provideRouter([]), { provide: ProductionFormulaApiService, useValue: { list } }],
    }).compileComponents();

    fixture = TestBed.createComponent(ProductionFormulaListPage);
    fixture.detectChanges();
  });

  it('should show loading feedback before formulas are returned', () => {
    expect(fixture.nativeElement.textContent).toContain('Carregando fórmulas de produção...');
  });

  it('should render formulas returned by the backend', () => {
    response.next([formula]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('output-item');
    expect(fixture.nativeElement.textContent).toContain('2 ingrediente(s)');
    const formulaLink = fixture.nativeElement.querySelector('mat-card a') as HTMLAnchorElement;
    expect(formulaLink.getAttribute('href')).toBe('/production/formulas/formula-1');
  });

  it('should render the empty state when no formulas exist', () => {
    response.next([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Nenhuma fórmula cadastrada');
  });

  it('should render a mapped backend error and retry the list request', () => {
    response.error(apiError());
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Fórmula de produção não encontrada.');

    response = new Subject<readonly ProductionFormulaDto[]>();
    list.mockReturnValue(response);
    const retryButton = findButton('Tentar novamente');
    retryButton.click();
    fixture.detectChanges();

    expect(list).toHaveBeenCalledTimes(2);
  });

  function findButton(text: string): HTMLButtonElement {
    return Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>).find(
      (button) => button.textContent?.includes(text),
    ) as HTMLButtonElement;
  }
});

function apiError(): HttpErrorResponse {
  return new HttpErrorResponse({
    status: 404,
    error: {
      timestamp: '2026-09-03T12:00:00Z',
      status: 404,
      error: 'Not Found',
      code: 'PRODUCTION_FORMULA_NOT_FOUND',
      message: 'Production formula was not found',
      path: '/api/v1/production/formulas',
      details: {},
    },
  });
}
