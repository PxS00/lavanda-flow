import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import { ProductionFormulaDto, UpsertProductionFormulaRequest } from './production-formula.dto';
import { ProductionFormulaApiService } from './production-formula-api.service';

describe('ProductionFormulaApiService', () => {
  const apiBaseUrl = 'https://api.example.test/';
  const formulasUrl = 'https://api.example.test/api/v1/production/formulas';
  const formula: ProductionFormulaDto = {
    id: 'formula-1',
    outputInventoryItemId: 'output-item',
    outputQuantity: 10.5,
    outputUnitOfMeasure: 'MILLILITER',
    ingredients: [
      { inventoryItemId: 'ingredient-a', quantity: 2.5, unitOfMeasure: 'MILLILITER' },
    ],
  };
  const requestBody: UpsertProductionFormulaRequest = {
    outputInventoryItemId: 'output-item',
    outputQuantity: 10.5,
    ingredients: [
      { inventoryItemId: 'ingredient-a', quantity: 2.5 },
      { inventoryItemId: 'ingredient-b', quantity: 1.25 },
    ],
  };

  let service: ProductionFormulaApiService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: apiBaseUrl },
      ],
    });

    service = TestBed.inject(ProductionFormulaApiService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('should list production formulas', () => {
    service.list().subscribe();

    const request = httpTesting.expectOne(formulasUrl);
    expect(request.request.method).toBe('GET');
    request.flush([formula]);
  });

  it('should retrieve one production formula by ID', () => {
    service.getById(formula.id).subscribe();

    const request = httpTesting.expectOne(`${formulasUrl}/${formula.id}`);
    expect(request.request.method).toBe('GET');
    request.flush(formula);
  });

  it('should create a formula with the exact backend request body', () => {
    service.create(requestBody).subscribe();

    const request = httpTesting.expectOne(formulasUrl);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(requestBody);
    request.flush(formula);
  });

  it('should replace a formula with the exact backend request body', () => {
    service.update(formula.id, requestBody).subscribe();

    const request = httpTesting.expectOne(`${formulasUrl}/${formula.id}`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(requestBody);
    request.flush(formula);
  });
});
