import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import { OrderApiService } from './order-api.service';

describe('OrderApiService', () => {
  let api: OrderApiService;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api/v1/' },
      ],
    });
    api = TestBed.inject(OrderApiService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('uses sale-scoped correction identity and exact decimals and retrieves persistent audit history', () => {
    const body = {
      lineId: 'line',
      batchId: 'batch',
      quantity: '1.000001',
      reason: 'Inspected',
      inspected: true,
      accepted: true,
    };
    api.correctStock('sale', 'correction', body).subscribe();
    const write = http.expectOne('/api/v1/sales/sale/stock-corrections/correction');
    expect(write.request.method).toBe('PUT');
    expect(write.request.body).toEqual(body);
    write.flush({ id: 'correction', movementId: 'movement' });
    api
      .stockCorrections('sale')
      .subscribe((history) =>
        expect(history).toEqual([{ id: 'correction', movementId: 'movement' }]),
      );
    const read = http.expectOne('/api/v1/sales/sale/stock-corrections');
    expect(read.request.method).toBe('GET');
    read.flush([{ id: 'correction', movementId: 'movement' }]);
  });
  it('confirms by persisted order identity without separate stock requests or retry tokens', () => {
    api.confirm('id').subscribe((result) => expect(result.status).toBe('CONFIRMED'));
    const request = http.expectOne('/api/v1/sales/id/confirm');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toBeNull();
    request.flush({ status: 'CONFIRMED' });
  });
  it('sends trimmed UUID search, customer, inclusive dates and bounded page fields', () => {
    api
      .search({
        q: ' abc ',
        customerId: 'customer',
        from: '2026-10-05',
        to: '2026-10-06',
        page: 2,
        size: 50,
      })
      .subscribe();
    const request = http.expectOne(
      '/api/v1/sales?page=2&size=50&q=abc&customerId=customer&from=2026-10-05&to=2026-10-06',
    );
    expect(request.request.method).toBe('GET');
    request.flush({ content: [] });
    api.search({ q: ' ', page: 0, size: 20 }).subscribe();
    http.expectOne('/api/v1/sales?page=0&size=20').flush({ content: [] });
  });
  it('serializes explicit status and independent confirmation dates without changing legacy dates', () => {
    api
      .search({
        page: 0,
        size: 20,
        status: 'CONFIRMED',
        from: '2026-09-01',
        confirmedFrom: '2026-10-01',
        confirmedTo: '2026-10-02',
      })
      .subscribe();
    const request = http.expectOne((req) => req.url === '/api/v1/sales');
    expect(request.request.params.get('status')).toBe('CONFIRMED');
    expect(request.request.params.get('from')).toBe('2026-09-01');
    expect(request.request.params.get('confirmedFrom')).toBe('2026-10-01');
    expect(request.request.params.get('confirmedTo')).toBe('2026-10-02');
    request.flush({ content: [] });
  });
  it('uses POST/GET/PUT contracts with optional customer and exact decimal strings without submitting totals', () => {
    const body = {
      customerId: null,
      lines: [{ id: null, itemId: 'item', quantity: '1234567890123.123456', unitPrice: '0.0001' }],
    };
    api.register(body).subscribe();
    const create = http.expectOne('/api/v1/sales');
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual(body);
    create.flush({});
    api.getById('id').subscribe((result) => expect(result.total).toBe('123456789.01'));
    http.expectOne('/api/v1/sales/id').flush({ total: '123456789.01' });
    api.update('id', body).subscribe();
    const edit = http.expectOne('/api/v1/sales/id');
    expect(edit.request.method).toBe('PUT');
    expect(edit.request.body).toEqual(body);
    edit.flush({});
  });
});
