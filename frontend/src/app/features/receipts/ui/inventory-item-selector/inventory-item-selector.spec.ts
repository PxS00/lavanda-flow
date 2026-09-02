import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { InventoryItemApiService } from '../../../catalog/data-access/inventory-item-api.service';
import { InventoryItemSelector } from './inventory-item-selector';

describe('InventoryItemSelector', () => {
  let fixture: ComponentFixture<InventoryItemSelector>;
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
      imports: [InventoryItemSelector],
      providers: [{ provide: InventoryItemApiService, useValue: { search } }],
    }).compileComponents();

    fixture = TestBed.createComponent(InventoryItemSelector);
    fixture.detectChanges();
    vi.advanceTimersByTime(200);
    fixture.detectChanges();
  });

  afterEach(() => vi.useRealTimers());

  it('should bound the initial search to active inventory items', () => {
    expect(search).toHaveBeenCalledWith({ name: '', active: true, page: 0, size: 10 });
  });
});
