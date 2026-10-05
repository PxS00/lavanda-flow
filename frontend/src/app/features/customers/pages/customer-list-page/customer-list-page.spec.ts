import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatPaginatorIntl } from '@angular/material/paginator';
import { provideRouter } from '@angular/router';
import { Observable, Subject } from 'rxjs';

import { CustomerPageDto, CustomerSearchQuery } from '../../data-access/customer.dto';
import { CustomerApiService } from '../../data-access/customer-api.service';
import { createPtBrPaginatorIntl } from '../../../../core/i18n/pt-br-paginator-intl';
import { CustomerListPage } from './customer-list-page';

describe('CustomerListPage', () => {
  const customer = {
    id: '53b1fdb4-72ab-41bb-b9e7-381d922d69a8',
    name: 'Lavanda Supplies',
    phone: '+5511999991234',
    email: 'customers@example.test',
    createdAt: '2026-10-05T12:00:00Z',
    updatedAt: '2026-10-05T12:00:00Z',
    active: true,
  };
  const populatedPage: CustomerPageDto = {
    content: [customer],
    page: 0,
    size: 20,
    totalElements: 21,
    totalPages: 2,
  };

  let fixture: ComponentFixture<CustomerListPage>;
  let response: Subject<CustomerPageDto>;
  let search: ReturnType<typeof vi.fn<(query: CustomerSearchQuery) => Observable<CustomerPageDto>>>;

  beforeEach(async () => {
    response = new Subject<CustomerPageDto>();
    search = vi.fn(() => response);

    await TestBed.configureTestingModule({
      imports: [CustomerListPage],
      providers: [
        provideRouter([]),
        { provide: CustomerApiService, useValue: { search } },
        { provide: MatPaginatorIntl, useFactory: createPtBrPaginatorIntl },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(CustomerListPage);
    fixture.detectChanges();
  });

  it('should issue the initial request and show loading feedback', () => {
    expect(search).toHaveBeenCalledWith({ page: 0, size: 20, active: true });
    expect(fixture.nativeElement.textContent).toContain('Carregando clientes...');
  });

  it('should render customers with detail and registration links', () => {
    response.next(populatedPage);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Lavanda Supplies');
    expect(fixture.nativeElement.textContent).toContain('+5511999991234');

    const links = Array.from(fixture.nativeElement.querySelectorAll('a')) as HTMLAnchorElement[];
    expect(links.some((link) => link.getAttribute('href') === `/customers/${customer.id}`)).toBe(
      true,
    );
    expect(links.some((link) => link.getAttribute('href') === '/customers/new')).toBe(true);
  });

  it('should guide an initially empty customer list toward the existing registration action', () => {
    response.next({ ...populatedPage, content: [], totalElements: 0, totalPages: 0 });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Nenhum cliente encontrado');
    expect(fixture.nativeElement.textContent).toContain('Cadastre um cliente ou altere o status');
    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeNull();
  });

  it('should keep a filtered empty result distinct from initial empty and error states', () => {
    response.next(populatedPage);
    fixture.componentInstance.filtersForm.setValue({ q: 'inexistente', active: 'all' });
    fixture.detectChanges();
    submitFilters();

    response.next({ ...populatedPage, content: [], totalElements: 0, totalPages: 0 });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Tente alterar ou redefinir os filtros');
    expect(fixture.nativeElement.textContent).not.toContain(
      'Cadastre um cliente ou altere o status',
    );
    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeNull();
  });

  it('should correct an empty out-of-range page once and render the last valid page', () => {
    response.next(populatedPage);
    fixture.detectChanges();

    const nextButton = fixture.nativeElement.querySelector(
      'button[aria-label="Próxima página"]',
    ) as HTMLButtonElement;
    nextButton.click();
    fixture.detectChanges();

    response.next({ content: [], page: 1, size: 20, totalElements: 20, totalPages: 1 });
    fixture.detectChanges();

    expect(search).toHaveBeenLastCalledWith({ page: 0, size: 20, active: true });
    expect(search).toHaveBeenCalledTimes(3);
    expect(fixture.nativeElement.textContent).toContain('Carregando clientes...');
    expect(fixture.nativeElement.textContent).not.toContain('No customers found');

    response.next({ ...populatedPage, page: 0, totalElements: 20, totalPages: 1 });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Lavanda Supplies');
    expect(search).toHaveBeenCalledTimes(3);
  });

  it('should render an error and retry the current request', () => {
    response.error(new HttpErrorResponse({ status: 0 }));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Não foi possível conectar ao servidor.');

    response = new Subject<CustomerPageDto>();
    search.mockReturnValue(response);
    clickButton('Tentar novamente');

    expect(search).toHaveBeenLastCalledWith({ page: 0, size: 20, active: true });
  });

  it('should apply a trimmed name and active true filter from page zero', () => {
    response.next(populatedPage);
    fixture.componentInstance.filtersForm.setValue({ q: '  lavanda  ', active: 'active' });
    fixture.detectChanges();

    submitFilters();

    expect(search).toHaveBeenLastCalledWith({
      q: 'lavanda',
      active: true,
      page: 0,
      size: 20,
    });
  });

  it('should preserve active false and omit a blank name', () => {
    response.next(populatedPage);
    fixture.componentInstance.filtersForm.setValue({ q: '   ', active: 'inactive' });
    fixture.detectChanges();

    submitFilters();

    expect(search).toHaveBeenLastCalledWith({ active: false, page: 0, size: 20 });
  });

  it('should reset filters and pagination to defaults', () => {
    response.next(populatedPage);
    fixture.componentInstance.filtersForm.setValue({ q: 'lavanda', active: 'active' });
    fixture.detectChanges();
    submitFilters();

    clickButton('Redefinir filtros');

    expect(fixture.componentInstance.filtersForm.getRawValue()).toEqual({
      q: '',
      active: 'active',
    });
    expect(search).toHaveBeenLastCalledWith({ page: 0, size: 20, active: true });
  });

  it('should request the selected zero-based page', () => {
    response.next(populatedPage);
    fixture.detectChanges();

    const nextButton = fixture.nativeElement.querySelector(
      'button[aria-label="Próxima página"]',
    ) as HTMLButtonElement;
    nextButton.click();
    fixture.detectChanges();

    expect(search).toHaveBeenLastCalledWith({ page: 1, size: 20, active: true });
  });

  it('should cancel an older request when a newer filter request starts', () => {
    const newerResponse = new Subject<CustomerPageDto>();
    search.mockReturnValueOnce(newerResponse);
    fixture.componentInstance.filtersForm.setValue({ q: 'new', active: 'all' });
    fixture.detectChanges();

    submitFilters();
    response.next(populatedPage);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Carregando clientes...');

    newerResponse.next({
      ...populatedPage,
      content: [{ ...customer, id: 'new-id', name: 'New customer' }],
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('New customer');
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
