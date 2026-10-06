import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Subject, of } from 'rxjs';
import { CustomerApiService } from '../../customers/data-access/customer-api.service';
import { CustomerPageDto } from '../../customers/data-access/customer.dto';
import { CustomerPicker } from './customer-picker';

describe('CustomerPicker', () => {
  it('searches on Enter without submitting the enclosing form, including while disabled', async () => {
    const search = vi.fn(() =>
      of({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
    );
    await TestBed.configureTestingModule({
      imports: [CustomerPicker],
      providers: [{ provide: CustomerApiService, useValue: { search } }],
    }).compileComponents();
    const fixture = TestBed.createComponent(CustomerPicker);
    fixture.detectChanges();
    fixture.componentInstance.searchText.setValue(' Ana ');
    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    const enter = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true });
    input.dispatchEvent(enter);
    expect(enter.defaultPrevented).toBe(true);
    expect(search).toHaveBeenLastCalledWith({ q: 'Ana', active: true, page: 0, size: 20 });
    expect(search).toHaveBeenCalledTimes(2);

    fixture.componentRef.setInput('disabled', true);
    fixture.detectChanges();
    const disabledEnter = new KeyboardEvent('keydown', {
      key: 'Enter',
      bubbles: true,
      cancelable: true,
    });
    input.dispatchEvent(disabledEnter);
    expect(disabledEnter.defaultPrevented).toBe(true);
    expect(search).toHaveBeenCalledTimes(2);
  });

  it('searches active contacts with bounded pages and emits stable identity', async () => {
    const customer = {
      id: 'customer',
      name: 'Ana',
      active: true,
      phone: null,
      email: null,
      createdAt: '',
      updatedAt: '',
    };
    const search = vi.fn(() =>
      of({
        content: [customer, { ...customer, id: 'inactive', name: 'Inativa', active: false }],
        totalElements: 40,
        page: 0,
      }),
    );
    await TestBed.configureTestingModule({
      imports: [CustomerPicker],
      providers: [{ provide: CustomerApiService, useValue: { search } }],
    }).compileComponents();
    const fixture = TestBed.createComponent(CustomerPicker);
    fixture.detectChanges();
    const selected = vi.fn();
    fixture.componentInstance.selected.subscribe(selected);
    expect(search).toHaveBeenCalledWith({ q: '', active: true, page: 0, size: 20 });
    expect(fixture.nativeElement.textContent).not.toContain('Inativa');
    fixture.nativeElement.querySelector('li button').click();
    expect(selected).toHaveBeenCalledWith(customer);
    fixture.nativeElement.querySelector('.mat-mdc-paginator-navigation-next').click();
    expect(search).toHaveBeenLastCalledWith({ q: '', active: true, page: 1, size: 20 });
  });

  it('cancels stale searches and preserves the current query through empty/error/retry states', async () => {
    const first = new Subject<CustomerPageDto>();
    const latest = new Subject<CustomerPageDto>();
    const search = vi.fn().mockReturnValueOnce(first).mockReturnValueOnce(latest);
    await TestBed.configureTestingModule({
      imports: [CustomerPicker],
      providers: [{ provide: CustomerApiService, useValue: { search } }],
    }).compileComponents();
    const fixture = TestBed.createComponent(CustomerPicker);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Buscando');
    fixture.componentInstance.searchText.setValue(' Ana ');
    (Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[])
      .find((button) => button.textContent?.trim() === 'Buscar')
      ?.click();
    expect(search).toHaveBeenLastCalledWith({ q: 'Ana', active: true, page: 0, size: 20 });
    latest.next({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    first.error(new HttpErrorResponse({ status: 500 }));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Nenhum resultado elegível');
    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeNull();

    latest.error(new HttpErrorResponse({ status: 500 }));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
    search.mockReturnValueOnce(
      of({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
    );
    (Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[])
      .find((button) => button.textContent?.includes('Tentar novamente'))
      ?.click();
    fixture.detectChanges();
    expect(search).toHaveBeenLastCalledWith({ q: 'Ana', active: true, page: 0, size: 20 });
    fixture.componentRef.setInput('disabled', true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('input').readOnly).toBe(true);
    expect(fixture.nativeElement.querySelector('button').disabled).toBe(true);
  });
});
