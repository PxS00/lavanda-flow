import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import { RegisterSupplierRequest, SupplierDto, SupplierPageDto } from './supplier.dto';
import { SupplierApiService } from './supplier-api.service';

describe('SupplierApiService', () => {
  const apiBaseUrl = 'https://api.example.test/';
  const suppliersUrl = 'https://api.example.test/api/v1/suppliers';
  const supplier: SupplierDto = {
    id: '53b1fdb4-72ab-41bb-b9e7-381d922d69a8',
    name: 'Lavanda Supplies',
    identifier: '12.345.678/0001-90',
    contact: 'suppliers@example.test',
    notes: 'Preferred supplier',
    active: true,
  };
  const page: SupplierPageDto = {
    content: [supplier],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
  };

  let service: SupplierApiService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: apiBaseUrl },
      ],
    });

    service = TestBed.inject(SupplierApiService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should request suppliers with explicit pagination', () => {
    service.search({ page: 2, size: 50 }).subscribe();

    const request = httpTesting.expectOne(`${suppliersUrl}?page=2&size=50`);

    expect(request.request.method).toBe('GET');
    request.flush(page);
  });

  it('should trim the name filter before sending it', () => {
    service.search({ name: '  lavanda  ', page: 0, size: 20 }).subscribe();

    const request = httpTesting.expectOne(`${suppliersUrl}?page=0&size=20&name=lavanda`);

    request.flush(page);
  });

  it('should omit a blank name filter', () => {
    service.search({ name: '   ', page: 0, size: 20 }).subscribe();

    const request = httpTesting.expectOne(`${suppliersUrl}?page=0&size=20`);

    expect(request.request.params.has('name')).toBe(false);
    request.flush(page);
  });

  it('should include active true when provided', () => {
    service.search({ active: true, page: 0, size: 20 }).subscribe();

    const request = httpTesting.expectOne(`${suppliersUrl}?page=0&size=20&active=true`);

    request.flush(page);
  });

  it('should include active false when provided', () => {
    service.search({ active: false, page: 0, size: 20 }).subscribe();

    const request = httpTesting.expectOne(`${suppliersUrl}?page=0&size=20&active=false`);

    request.flush(page);
  });

  it('should omit undefined optional filters', () => {
    service.search({ name: undefined, active: undefined, page: 0, size: 20 }).subscribe();

    const request = httpTesting.expectOne(`${suppliersUrl}?page=0&size=20`);

    expect(request.request.params.keys()).toEqual(['page', 'size']);
    request.flush(page);
  });

  it('should retrieve a supplier by ID', () => {
    let result: SupplierDto | undefined;

    service.getById(supplier.id).subscribe((response) => {
      result = response;
    });

    const request = httpTesting.expectOne(`${suppliersUrl}/${supplier.id}`);

    expect(request.request.method).toBe('GET');
    request.flush(supplier);
    expect(result).toEqual(supplier);
  });

  it('should register a supplier with the exact request body', () => {
    const requestBody: RegisterSupplierRequest = {
      name: 'Lavanda Supplies',
      identifier: '12.345.678/0001-90',
      contact: 'suppliers@example.test',
      notes: 'Preferred supplier',
    };
    let result: SupplierDto | undefined;

    service.register(requestBody).subscribe((response) => {
      result = response;
    });

    const request = httpTesting.expectOne(suppliersUrl);

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(requestBody);
    request.flush(supplier);
    expect(result).toEqual(supplier);
  });
});
