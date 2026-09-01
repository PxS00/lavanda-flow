import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, Subject } from 'rxjs';

import {
  InventoryItemPageDto,
  InventoryItemSearchQuery,
} from '../../data-access/inventory-item.dto';
import { InventoryItemApiService } from '../../data-access/inventory-item-api.service';
import { InventoryItemListPage } from './inventory-item-list-page';

describe('InventoryItemListPage', () => {
  const item = {
    id: 'bd194732-51cf-4f73-bc5d-3a9f9337adcc',
    name: 'Lavender Essence',
    description: null,
    category: 'ESSENCE' as const,
    unitOfMeasure: 'MILLILITER' as const,
    active: true,
  };
  const populatedPage: InventoryItemPageDto = {
    content: [item],
    page: 0,
    size: 20,
    totalElements: 21,
    totalPages: 2,
  };

  let fixture: ComponentFixture<InventoryItemListPage>;
  let response: Subject<InventoryItemPageDto>;
  let search: ReturnType<
    typeof vi.fn<(query: InventoryItemSearchQuery) => Observable<InventoryItemPageDto>>
  >;

  beforeEach(async () => {
    response = new Subject<InventoryItemPageDto>();
    search = vi.fn(() => response);

    await TestBed.configureTestingModule({
      imports: [InventoryItemListPage],
      providers: [provideRouter([]), { provide: InventoryItemApiService, useValue: { search } }],
    }).compileComponents();

    fixture = TestBed.createComponent(InventoryItemListPage);
    fixture.detectChanges();
  });

  it('should issue the initial request and show loading feedback', () => {
    expect(search).toHaveBeenCalledWith({ page: 0, size: 20 });
    expect(fixture.nativeElement.textContent).toContain('Loading inventory items...');
  });

  it('should render returned items with detail and registration links', () => {
    response.next(populatedPage);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Lavender Essence');
    expect(fixture.nativeElement.textContent).toContain('Essence');

    const links = Array.from(fixture.nativeElement.querySelectorAll('a')) as HTMLAnchorElement[];
    expect(links.some((link) => link.getAttribute('href') === `/catalog/${item.id}`)).toBe(true);
    expect(links.some((link) => link.getAttribute('href') === '/catalog/new')).toBe(true);
  });

  it('should render the empty state', () => {
    response.next({ ...populatedPage, content: [], totalElements: 0, totalPages: 0 });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No inventory items found');
  });

  it('should correct an empty out-of-range page once and render the last valid page', () => {
    response.next(populatedPage);
    fixture.detectChanges();

    const nextButton = fixture.nativeElement.querySelector(
      'button[aria-label="Next page"]',
    ) as HTMLButtonElement;
    nextButton.click();
    fixture.detectChanges();

    response.next({
      content: [],
      page: 1,
      size: 20,
      totalElements: 20,
      totalPages: 1,
    });
    fixture.detectChanges();

    expect(search).toHaveBeenLastCalledWith({ page: 0, size: 20 });
    expect(search).toHaveBeenCalledTimes(3);
    expect(fixture.nativeElement.textContent).toContain('Loading inventory items...');
    expect(fixture.nativeElement.textContent).not.toContain('No inventory items found');

    response.next({ ...populatedPage, page: 0, totalElements: 20, totalPages: 1 });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Lavender Essence');
    expect(search).toHaveBeenCalledTimes(3);
  });

  it('should render an error and retry the current request', () => {
    response.error(new HttpErrorResponse({ status: 0 }));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Unable to connect to the server.');

    response = new Subject<InventoryItemPageDto>();
    search.mockReturnValue(response);
    clickButton('Try again');

    expect(search).toHaveBeenLastCalledWith({ page: 0, size: 20 });
  });

  it('should apply trimmed name, category, and active true filters from page zero', () => {
    response.next(populatedPage);
    fixture.componentInstance.filtersModel.set({
      name: '  lavender  ',
      category: 'ESSENCE',
      active: 'active',
    });
    fixture.detectChanges();

    submitFilters();

    expect(search).toHaveBeenLastCalledWith({
      name: 'lavender',
      category: 'ESSENCE',
      active: true,
      page: 0,
      size: 20,
    });
  });

  it('should preserve active false and omit blank optional filters', () => {
    response.next(populatedPage);
    fixture.componentInstance.filtersModel.set({ name: '   ', category: '', active: 'inactive' });
    fixture.detectChanges();

    submitFilters();

    expect(search).toHaveBeenLastCalledWith({ active: false, page: 0, size: 20 });
  });

  it('should reset filters and pagination to defaults', () => {
    response.next(populatedPage);
    fixture.componentInstance.filtersModel.set({
      name: 'lavender',
      category: 'ESSENCE',
      active: 'active',
    });
    fixture.detectChanges();
    submitFilters();

    clickButton('Reset');

    expect(fixture.componentInstance.filtersModel()).toEqual({
      name: '',
      category: '',
      active: 'all',
    });
    expect(search).toHaveBeenLastCalledWith({ page: 0, size: 20 });
  });

  it('should request the selected zero-based page', () => {
    response.next(populatedPage);
    fixture.detectChanges();

    const nextButton = fixture.nativeElement.querySelector(
      'button[aria-label="Next page"]',
    ) as HTMLButtonElement;
    nextButton.click();
    fixture.detectChanges();

    expect(search).toHaveBeenLastCalledWith({ page: 1, size: 20 });
  });

  it('should cancel an older request when a newer filter request starts', () => {
    const newerResponse = new Subject<InventoryItemPageDto>();
    search.mockReturnValueOnce(newerResponse);
    fixture.componentInstance.filtersModel.set({ name: 'new', category: '', active: 'all' });
    fixture.detectChanges();

    submitFilters();
    response.next(populatedPage);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Loading inventory items...');

    newerResponse.next({
      ...populatedPage,
      content: [{ ...item, id: 'new-id', name: 'New item' }],
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('New item');
    expect(fixture.nativeElement.textContent).not.toContain('Lavender Essence');
  });

  function submitFilters(): void {
    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new SubmitEvent('submit', { bubbles: true, cancelable: true }));
    fixture.detectChanges();
  }

  function clickButton(label: string): void {
    const buttons = Array.from(
      fixture.nativeElement.querySelectorAll('button'),
    ) as HTMLButtonElement[];
    const button = buttons.find((candidate) => candidate.textContent?.trim() === label);
    expect(button).toBeDefined();
    button?.click();
    fixture.detectChanges();
  }
});
