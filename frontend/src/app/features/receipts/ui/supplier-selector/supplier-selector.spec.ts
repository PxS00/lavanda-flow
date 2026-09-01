import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { SupplierApiService } from '../../../suppliers/data-access/supplier-api.service';
import { SupplierSelector } from './supplier-selector';

describe('SupplierSelector', () => {
  let fixture: ComponentFixture<SupplierSelector>;
  let search: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    vi.useFakeTimers();
    search = vi.fn(() =>
      of({
        content: [],
        page: 0,
        size: 10,
        totalElements: 0,
        totalPages: 0,
      }),
    );

    await TestBed.configureTestingModule({
      imports: [SupplierSelector],
      providers: [{ provide: SupplierApiService, useValue: { search } }],
    }).compileComponents();

    fixture = TestBed.createComponent(SupplierSelector);
    fixture.detectChanges();
    vi.advanceTimersByTime(200);
    fixture.detectChanges();
  });

  afterEach(() => vi.useRealTimers());

  it('should bound the initial search to active suppliers', () => {
    expect(search).toHaveBeenCalledWith({ name: '', active: true, page: 0, size: 10 });
  });
});
