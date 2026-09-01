import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import {
  InventoryItemDto,
  InventoryItemPageDto,
  RegisterInventoryItemRequest,
} from './inventory-item.dto';
import { InventoryItemApiService } from './inventory-item-api.service';

describe('InventoryItemApiService', () => {
  const apiBaseUrl = 'https://api.example.test/';
  const inventoryItemsUrl = 'https://api.example.test/api/v1/inventory-items';
  const item: InventoryItemDto = {
    id: 'bd194732-51cf-4f73-bc5d-3a9f9337adcc',
    name: 'Lavender Essence',
    description: 'Floral raw material',
    category: 'ESSENCE',
    unitOfMeasure: 'MILLILITER',
    active: true,
  };
  const page: InventoryItemPageDto = {
    content: [item],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
  };

  let service: InventoryItemApiService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: apiBaseUrl },
      ],
    });

    service = TestBed.inject(InventoryItemApiService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should request the paginated inventory-item list with page and size', () => {
    service.search({ page: 2, size: 10 }).subscribe();

    const request = httpTesting.expectOne(
      `${inventoryItemsUrl}?page=2&size=10`,
    );

    expect(request.request.method).toBe('GET');
    request.flush(page);
  });

  it('should trim a name filter before sending it', () => {
    service.search({ name: '  lavender  ', page: 0, size: 20 }).subscribe();

    const request = httpTesting.expectOne(
      `${inventoryItemsUrl}?page=0&size=20&name=lavender`,
    );

    request.flush(page);
  });

  it('should omit a blank name filter', () => {
    service.search({ name: '   ', page: 0, size: 20 }).subscribe();

    const request = httpTesting.expectOne(
      `${inventoryItemsUrl}?page=0&size=20`,
    );

    expect(request.request.params.has('name')).toBe(false);
    request.flush(page);
  });

  it('should include provided category and active true filters', () => {
    service.search({ category: 'ESSENCE', active: true, page: 0, size: 20 }).subscribe();

    const request = httpTesting.expectOne(
      `${inventoryItemsUrl}?page=0&size=20&category=ESSENCE&active=true`,
    );

    request.flush(page);
  });

  it('should include an active false filter', () => {
    service.search({ active: false, page: 0, size: 20 }).subscribe();

    const request = httpTesting.expectOne(
      `${inventoryItemsUrl}?page=0&size=20&active=false`,
    );

    request.flush(page);
  });

  it('should omit optional filters when they are undefined', () => {
    service
      .search({ name: undefined, category: undefined, active: undefined, page: 0, size: 20 })
      .subscribe();

    const request = httpTesting.expectOne(
      `${inventoryItemsUrl}?page=0&size=20`,
    );

    expect(request.request.params.keys()).toEqual(['page', 'size']);
    request.flush(page);
  });

  it('should retrieve an inventory item by ID and return the response', () => {
    let result: InventoryItemDto | undefined;

    service.getById(item.id).subscribe((response) => {
      result = response;
    });

    const request = httpTesting.expectOne(`${inventoryItemsUrl}/${item.id}`);

    expect(request.request.method).toBe('GET');
    request.flush(item);

    expect(result).toEqual(item);
  });

  it('should register an inventory item with the exact request body', () => {
    const requestBody: RegisterInventoryItemRequest = {
      name: 'Lavender Essence',
      description: 'Floral raw material',
      category: 'ESSENCE',
      unitOfMeasure: 'MILLILITER',
    };
    let result: InventoryItemDto | undefined;

    service.register(requestBody).subscribe((response) => {
      result = response;
    });

    const request = httpTesting.expectOne(inventoryItemsUrl);

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(requestBody);
    request.flush(item);

    expect(result).toEqual(item);
  });
});
