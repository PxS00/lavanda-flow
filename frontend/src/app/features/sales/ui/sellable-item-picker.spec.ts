import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Subject, of } from 'rxjs';
import { InventoryItemApiService } from '../../catalog/data-access/inventory-item-api.service';
import { InventoryItemPageDto } from '../../catalog/data-access/inventory-item.dto';
import { product } from '../testing/order-fixture';
import { SellableItemPicker } from './sellable-item-picker';

describe('SellableItemPicker', () => {
  it('offers only active finished UNIT/mL identities, searches and pages instead of truncating selection', async () => {
    const search = vi.fn(() =>
      of({
        content: [
          product,
          { ...product, id: 'bulk', name: 'Granel', unitOfMeasure: 'MILLILITER' },
          { ...product, id: 'liter', name: 'Litros', unitOfMeasure: 'LITER' },
          { ...product, id: 'input', name: 'Insumo', category: 'OTHER' },
          { ...product, id: 'inactive', name: 'Inativo', active: false },
        ],
        totalElements: 40,
        page: 0,
        size: 20,
        totalPages: 2,
      }),
    );
    await TestBed.configureTestingModule({
      imports: [SellableItemPicker],
      providers: [{ provide: InventoryItemApiService, useValue: { search } }],
    }).compileComponents();
    const fixture = TestBed.createComponent(SellableItemPicker);
    fixture.detectChanges();
    const selected = vi.fn();
    fixture.componentInstance.selected.subscribe(selected);
    expect(search).toHaveBeenCalledWith({
      name: '',
      category: 'FINISHED_PRODUCT',
      active: true,
      page: 0,
      size: 20,
    });
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Perfume');
    expect(text).toContain('Granel');
    expect(text).not.toContain('Litros');
    expect(text).not.toContain('Insumo');
    expect(text).not.toContain('Inativo');
    const buttons = Array.from(
      fixture.nativeElement.querySelectorAll('li button'),
    ) as HTMLButtonElement[];
    buttons[1].click();
    expect(selected.mock.calls[0][0].id).toBe('bulk');
    fixture.nativeElement.querySelector('.mat-mdc-paginator-navigation-next').click();
    expect(search).toHaveBeenLastCalledWith({
      name: '',
      category: 'FINISHED_PRODUCT',
      active: true,
      page: 1,
      size: 20,
    });
    fixture.componentInstance.searchText.setValue(' perfume ');
    (Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[])
      .find((b) => b.textContent?.trim() === 'Buscar')
      ?.click();
    expect(search).toHaveBeenLastCalledWith({
      name: 'perfume',
      category: 'FINISHED_PRODUCT',
      active: true,
      page: 0,
      size: 20,
    });
    fixture.componentRef.setInput('disabled', true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('li button').disabled).toBe(true);
  });
  it('shows loading, empty-page and recoverable failure with explicit retry', async () => {
    const response = new Subject<InventoryItemPageDto>();
    const search = vi.fn(() => response.asObservable());
    await TestBed.configureTestingModule({
      imports: [SellableItemPicker],
      providers: [{ provide: InventoryItemApiService, useValue: { search } }],
    }).compileComponents();
    const fixture = TestBed.createComponent(SellableItemPicker);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Buscando');
    response.next({ content: [], totalElements: 0, page: 0, size: 20, totalPages: 0 });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Nenhum resultado elegível');
    response.error(new HttpErrorResponse({ status: 500 }));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
    search.mockReturnValueOnce(
      of({ content: [product], totalElements: 1, page: 0, size: 20, totalPages: 1 }),
    );
    (Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[])
      .find((b) => b.textContent?.includes('Tentar novamente'))
      ?.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Perfume');
  });
});
