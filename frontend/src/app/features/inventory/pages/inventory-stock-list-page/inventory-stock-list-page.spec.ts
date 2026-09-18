import { HttpErrorResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatPaginator, MatPaginatorIntl } from '@angular/material/paginator';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { Observable, Subject } from 'rxjs';

import { createPtBrPaginatorIntl } from '../../../../core/i18n/pt-br-paginator-intl';
import { InventoryStockListApiService } from '../../data-access/inventory-stock-list-api.service';
import {
  InventoryStockListPageDto,
  InventoryStockListQuery,
} from '../../data-access/inventory-stock-list.dto';
import { InventoryStockListPage } from './inventory-stock-list-page';

describe('InventoryStockListPage', () => {
  let fixture: ComponentFixture<InventoryStockListPage>;
  let response: Subject<InventoryStockListPageDto>;
  let search: ReturnType<
    typeof vi.fn<(query: InventoryStockListQuery) => Observable<InventoryStockListPageDto>>
  >;

  beforeEach(async () => {
    response = new Subject<InventoryStockListPageDto>();
    search = vi.fn(() => response);
    await TestBed.configureTestingModule({
      imports: [InventoryStockListPage],
      providers: [
        provideRouter([]),
        { provide: InventoryStockListApiService, useValue: { search } },
        { provide: MatPaginatorIntl, useFactory: createPtBrPaginatorIntl },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              data: {
                title: 'Produtos finalizados',
                helperText: 'Consulte produtos finalizados.',
                categories: ['FINISHED_PRODUCT'],
              },
            },
          },
        },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(InventoryStockListPage);
    fixture.detectChanges();
  });

  it('should request the route category set and show loading', () => {
    expect(search).toHaveBeenCalledWith({ categories: ['FINISHED_PRODUCT'], page: 0, size: 20 });
    expect(fixture.nativeElement.textContent).toContain('Carregando estoque');
  });

  it('should render backend metrics, references, units, status and active actions', () => {
    response.next(page([
      item('bulk', 'Produto a granel', 'MILLILITER', true, false, false),
      item('packaged', 'Produto 30 ml', 'UNIT', true, true, false),
    ]));
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Produto finalizado');
    expect(text).toContain('10,125 Mililitro');
    expect(text).toContain('10,125 Unidade');
    expect(text).toContain('Estoque baixo');
    expect(text).toContain('Referência da essência: 027');
    expect(text).toContain('Tipo de produção: BULK');
    expect(text).toContain('2 lote(s) com saldo');
    const links = Array.from(fixture.nativeElement.querySelectorAll('a')) as HTMLAnchorElement[];
    expect(links.some((link) => link.getAttribute('href') === '/inventory/items/bulk')).toBe(true);
    expect(links.some((link) => link.getAttribute('href') === '/receipts?inventoryItemId=bulk')).toBe(true);
    expect(links.find((link) => link.textContent?.trim() === 'Registrar saída')?.getAttribute('href'))
      .toBe('/inventory/items/bulk');
  });

  it('should expose links to every grouped stock workspace', () => {
    const links = Array.from(
      fixture.nativeElement.querySelectorAll('.group-navigation a'),
    ) as HTMLAnchorElement[];

    expect(links.map((link) => [link.textContent?.trim(), link.getAttribute('href')])).toEqual([
      ['Todo o estoque', '/inventory/all'],
      ['Produtos finalizados', '/inventory/finished-products'],
      ['Essências', '/inventory/essences'],
      ['Insumos', '/inventory/inputs'],
      ['Embalagens e componentes', '/inventory/packaging'],
    ]);
  });

  it('should keep inactive rows inspectable without mutation shortcuts', () => {
    response.next(page([item('inactive', 'Produto inativo', 'UNIT', false, false, true)]));
    fixture.detectChanges();

    const links = Array.from(fixture.nativeElement.querySelectorAll('.actions a')) as HTMLAnchorElement[];
    expect(links.map((link) => link.textContent?.trim())).toEqual(['Abrir estoque']);
    expect(fixture.nativeElement.textContent).toContain('Inativo');
    expect(fixture.nativeElement.textContent).toContain('Sem estoque');
  });

  it('should request the selected page', () => {
    response.next(page([item('bulk', 'Produto a granel', 'MILLILITER', true, false, false)]));
    fixture.detectChanges();

    paginator().page.emit({ pageIndex: 2, pageSize: 20, length: 60, previousPageIndex: 1 });

    expect(search).toHaveBeenLastCalledWith({
      categories: ['FINISHED_PRODUCT'], page: 2, size: 20,
    });
  });

  it('should request the selected page size', () => {
    response.next(page([item('bulk', 'Produto a granel', 'MILLILITER', true, false, false)]));
    fixture.detectChanges();

    paginator().page.emit({ pageIndex: 0, pageSize: 50, length: 60, previousPageIndex: 0 });

    expect(search).toHaveBeenLastCalledWith({
      categories: ['FINISHED_PRODUCT'], page: 0, size: 50,
    });
  });

  it('should render empty and error states and retry the same query', () => {
    response.next(page([]));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Nenhum item encontrado');

    response = new Subject<InventoryStockListPageDto>();
    search.mockReturnValue(response);
    (fixture.componentInstance as unknown as { retry(): void }).retry();
    response.error(new HttpErrorResponse({ status: 0 }));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Não foi possível conectar ao servidor');

    response = new Subject<InventoryStockListPageDto>();
    search.mockReturnValue(response);
    (fixture.componentInstance as unknown as { retry(): void }).retry();
    expect(search).toHaveBeenLastCalledWith({ categories: ['FINISHED_PRODUCT'], page: 0, size: 20 });
  });

  function item(
    id: string,
    name: string,
    unitOfMeasure: 'MILLILITER' | 'UNIT',
    active: boolean,
    lowStock: boolean,
    outOfStock: boolean,
  ) {
    return {
      inventoryItemId: id,
      name,
      category: 'FINISHED_PRODUCT' as const,
      unitOfMeasure,
      active,
      essenceReference: '027',
      productionTypeCode: 'BULK',
      totalCurrentQuantity: '10.125000',
      availableQuantity: '4.500000',
      minimumQuantity: '5.000000',
      lowStock,
      outOfStock,
      nonZeroBatchCount: 2,
      nearestExpiration: '2026-09-30',
    };
  }

  function page(content: InventoryStockListPageDto['content']): InventoryStockListPageDto {
    return {
      content,
      page: 0,
      size: 20,
      totalElements: content.length,
      totalPages: content.length ? 1 : 0,
      asOfDate: '2026-09-18',
      expirationWindowDays: 30,
    };
  }

  function paginator(): MatPaginator {
    return fixture.debugElement.query(By.directive(MatPaginator)).componentInstance as MatPaginator;
  }
});
