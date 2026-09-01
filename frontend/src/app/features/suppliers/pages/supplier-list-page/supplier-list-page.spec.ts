import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, Subject } from 'rxjs';

import { SupplierPageDto, SupplierSearchQuery } from '../../data-access/supplier.dto';
import { SupplierApiService } from '../../data-access/supplier-api.service';
import { SupplierListPage } from './supplier-list-page';

describe('SupplierListPage', () => {
  const supplier = {
    id: '53b1fdb4-72ab-41bb-b9e7-381d922d69a8',
    name: 'Lavanda Supplies',
    identifier: '12.345.678/0001-90',
    contact: 'suppliers@example.test',
    notes: null,
    active: true,
  };
  const populatedPage: SupplierPageDto = {
    content: [supplier],
    page: 0,
    size: 20,
    totalElements: 21,
    totalPages: 2,
  };

  let fixture: ComponentFixture<SupplierListPage>;
  let response: Subject<SupplierPageDto>;
  let search: ReturnType<typeof vi.fn<(query: SupplierSearchQuery) => Observable<SupplierPageDto>>>;

  beforeEach(async () => {
    response = new Subject<SupplierPageDto>();
    search = vi.fn(() => response);

    await TestBed.configureTestingModule({
      imports: [SupplierListPage],
      providers: [provideRouter([]), { provide: SupplierApiService, useValue: { search } }],
    }).compileComponents();

    fixture = TestBed.createComponent(SupplierListPage);
    fixture.detectChanges();
  });

  it('should issue the initial request and show loading feedback', () => {
    expect(search).toHaveBeenCalledWith({ page: 0, size: 20 });
    expect(fixture.nativeElement.textContent).toContain('Loading suppliers...');
  });

  it('should render suppliers with detail and registration links', () => {
    response.next(populatedPage);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Lavanda Supplies');
    expect(fixture.nativeElement.textContent).toContain('12.345.678/0001-90');

    const links = Array.from(fixture.nativeElement.querySelectorAll('a')) as HTMLAnchorElement[];
    expect(links.some((link) => link.getAttribute('href') === `/suppliers/${supplier.id}`)).toBe(true);
    expect(links.some((link) => link.getAttribute('href') === '/suppliers/new')).toBe(true);
  });

  it('should render the empty state', () => {
    response.next({ ...populatedPage, content: [], totalElements: 0, totalPages: 0 });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No suppliers found');
  });

  it('should correct an empty out-of-range page once and render the last valid page', () => {
    response.next(populatedPage);
    fixture.detectChanges();

    const nextButton = fixture.nativeElement.querySelector(
      'button[aria-label="Next page"]',
    ) as HTMLButtonElement;
    nextButton.click();
    fixture.detectChanges();

    response.next({ content: [], page: 1, size: 20, totalElements: 20, totalPages: 1 });
    fixture.detectChanges();

    expect(search).toHaveBeenLastCalledWith({ page: 0, size: 20 });
    expect(search).toHaveBeenCalledTimes(3);
    expect(fixture.nativeElement.textContent).toContain('Loading suppliers...');
    expect(fixture.nativeElement.textContent).not.toContain('No suppliers found');

    response.next({ ...populatedPage, page: 0, totalElements: 20, totalPages: 1 });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Lavanda Supplies');
    expect(search).toHaveBeenCalledTimes(3);
  });

  it('should render an error and retry the current request', () => {
    response.error(new HttpErrorResponse({ status: 0 }));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Unable to connect to the server.');

    response = new Subject<SupplierPageDto>();
    search.mockReturnValue(response);
    clickButton('Try again');

    expect(search).toHaveBeenLastCalledWith({ page: 0, size: 20 });
  });

  it('should apply a trimmed name and active true filter from page zero', () => {
    response.next(populatedPage);
    fixture.componentInstance.filtersModel.set({ name: '  lavanda  ', active: 'active' });
    fixture.detectChanges();

    submitFilters();

    expect(search).toHaveBeenLastCalledWith({
      name: 'lavanda',
      active: true,
      page: 0,
      size: 20,
    });
  });

  it('should preserve active false and omit a blank name', () => {
    response.next(populatedPage);
    fixture.componentInstance.filtersModel.set({ name: '   ', active: 'inactive' });
    fixture.detectChanges();

    submitFilters();

    expect(search).toHaveBeenLastCalledWith({ active: false, page: 0, size: 20 });
  });

  it('should reset filters and pagination to defaults', () => {
    response.next(populatedPage);
    fixture.componentInstance.filtersModel.set({ name: 'lavanda', active: 'active' });
    fixture.detectChanges();
    submitFilters();

    clickButton('Reset');

    expect(fixture.componentInstance.filtersModel()).toEqual({ name: '', active: 'all' });
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
    const newerResponse = new Subject<SupplierPageDto>();
    search.mockReturnValueOnce(newerResponse);
    fixture.componentInstance.filtersModel.set({ name: 'new', active: 'all' });
    fixture.detectChanges();

    submitFilters();
    response.next(populatedPage);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Loading suppliers...');

    newerResponse.next({
      ...populatedPage,
      content: [{ ...supplier, id: 'new-id', name: 'New supplier' }],
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('New supplier');
    expect(fixture.nativeElement.textContent).not.toContain('Lavanda Supplies');
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
