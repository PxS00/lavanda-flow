import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import { MovementHistoryPageDto } from './inventory-operations.dto';
import { MovementHistoryApiService } from './movement-history-api.service';

describe('MovementHistoryApiService', () => {
  const inventoryItemId = 'bd194732-51cf-4f73-bc5d-3a9f9337adcc';
  const movementsUrl = 'https://api.example.test/api/v1/inventory/movements';
  const page: MovementHistoryPageDto = {
    content: [],
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
  };

  let service: MovementHistoryApiService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'https://api.example.test/api/v1/' },
      ],
    });

    service = TestBed.inject(MovementHistoryApiService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('should scope movement history to the inventory item with explicit pagination', () => {
    let result: MovementHistoryPageDto | undefined;
    service.search({ inventoryItemId, page: 2, size: 50 }).subscribe((response) => (result = response));

    const request = httpTesting.expectOne(
      `${movementsUrl}?inventoryItemId=${inventoryItemId}&page=2&size=50`,
    );
    expect(request.request.method).toBe('GET');
    request.flush(page);

    expect(result).toEqual(page);
  });
});
