import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { InventoryItemDto } from '../../data-access/inventory-item.dto';
import { InventoryItemApiService } from '../../data-access/inventory-item-api.service';
import { InventoryItemSelector } from './inventory-item-selector';

describe('InventoryItemSelector', () => {
  const referencedItem: InventoryItemDto = {
    id: 'item-1',
    name: 'Essência de lavanda',
    description: null,
    category: 'ESSENCE',
    unitOfMeasure: 'MILLILITER',
    active: true,
    essenceReference: '027',
    productionTypeCode: 'BHC',
  };
  const itemWithoutReferences: InventoryItemDto = {
    ...referencedItem,
    id: 'item-2',
    name: 'Base neutra',
    essenceReference: null,
    productionTypeCode: null,
  };
  let fixture: ComponentFixture<InventoryItemSelector>;
  let search: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    vi.useFakeTimers();
    search = vi.fn(() =>
      of({
        content: [referencedItem, itemWithoutReferences],
        page: 0,
        size: 10,
        totalElements: 2,
        totalPages: 1,
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

  it('should show reference metadata in results without fabricating null values', () => {
    const resultButtons = fixture.nativeElement.querySelectorAll(
      '.results button',
    ) as NodeListOf<HTMLButtonElement>;

    expect(resultButtons[0].textContent).toContain('Ref. essência:');
    expect(resultButtons[0].textContent).toContain('027');
    expect(resultButtons[0].textContent).toContain('Cód. produção:');
    expect(resultButtons[0].textContent).toContain('BHC');
    expect(resultButtons[1].textContent).not.toContain('Ref. essência');
    expect(resultButtons[1].textContent).not.toMatch(/000|---|N\/A/);
  });

  it('should emit the original DTO unchanged and preserve select and clear behavior', () => {
    const emitted: (InventoryItemDto | null)[] = [];
    fixture.componentInstance.selectionChange.subscribe((item) => emitted.push(item));

    (fixture.nativeElement.querySelector('.results button') as HTMLButtonElement).click();
    fixture.componentRef.setInput('selected', referencedItem);
    fixture.detectChanges();

    expect(emitted[0]).toBe(referencedItem);
    expect(fixture.nativeElement.querySelector('.selected')?.textContent).toContain(
      'Ref. essência:',
    );

    const clearButton = Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>,
    ).find((button) => button.textContent?.trim() === 'Limpar') as HTMLButtonElement;
    clearButton.click();
    vi.advanceTimersByTime(200);
    fixture.detectChanges();

    expect(emitted).toEqual([referencedItem, null]);
    expect(search).toHaveBeenLastCalledWith({ name: '', active: true, page: 0, size: 10 });
  });
});
