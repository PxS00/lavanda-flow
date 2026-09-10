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

  it('associates the optional supplier helper without changing selector guidance', () => {
    const searchField = fixture.nativeElement.querySelector('.search-field') as HTMLElement;
    const input = searchField.querySelector('input') as HTMLInputElement;
    const helper = searchField.querySelector('#supplier-search-hint') as HTMLParagraphElement;

    expect(helper.textContent?.trim()).toBe('Deixe sem seleção quando a entrada não tiver fornecedor');
    expect(input.getAttribute('aria-describedby')).toContain('supplier-search-hint');
    expect(fixture.nativeElement.querySelector('.selector-heading h2')?.textContent?.trim()).toBe(
      'Fornecedor (opcional)',
    );
    expect(fixture.nativeElement.querySelector('.selector-heading p')?.textContent?.trim()).toBe(
      'Busca apenas fornecedores ativos.',
    );
    expect(fixture.nativeElement.querySelector('mat-hint')).toBeNull();
  });
});
