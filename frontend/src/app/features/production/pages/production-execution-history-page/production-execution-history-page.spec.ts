import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { Subject } from 'rxjs';

import { ProductionExecutionApiService } from '../../data-access/production-execution-api.service';
import { ProductionExecutionHistoryPageDto } from '../../data-access/production-execution.dto';
import { ProductionExecutionHistoryPage } from './production-execution-history-page';

describe('ProductionExecutionHistoryPage', () => {
  let fixture: ComponentFixture<ProductionExecutionHistoryPage>;
  let response: Subject<ProductionExecutionHistoryPageDto>;
  let search: ReturnType<typeof vi.fn>;

  const populatedPage: ProductionExecutionHistoryPageDto = {
    content: [
      {
        executionId: 'execution-1',
        formulaId: 'formula-1',
        outputInventoryItemId: 'item-1',
        outputItemName: 'Sabonete líquido',
        outputUnitOfMeasure: 'MILLILITER',
        outputBatchId: 'batch-1',
        outputQuantity: '1234.500000',
        lotCode: 'LOT-001',
        lotCodeMode: 'GENERATED',
        productionDate: '2026-09-18',
        completedAt: '2026-09-18T12:00:00Z',
      },
    ],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
  };

  beforeEach(async () => {
    response = new Subject<ProductionExecutionHistoryPageDto>();
    search = vi.fn(() => response);
    await TestBed.configureTestingModule({
      imports: [ProductionExecutionHistoryPage],
      providers: [
        provideRouter([]),
        { provide: ProductionExecutionApiService, useValue: { search } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ProductionExecutionHistoryPage);
    fixture.detectChanges();
  });

  it('should show loading and backend content with exact localized values and identities', () => {
    expect(fixture.nativeElement.textContent).toContain('Carregando histórico de produção');

    response.next(populatedPage);
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Sabonete líquido');
    expect(text).toContain('1.234,5 Mililitro');
    expect(text).toContain('18/09/2026');
    expect(text).toContain('LOT-001');
    expect(text).toContain('formula-1');
    expect(text).toContain('execution-1');
    expect(
      fixture.nativeElement.querySelector('a[href="/production/executions/execution-1"]'),
    ).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('a[href="/production/executions/new"]'),
    ).toBeTruthy();
  });

  it('should send filters and paginator state to the backend without local slicing', () => {
    response.next(populatedPage);
    fixture.detectChanges();
    const component = fixture.componentInstance as unknown as {
      filters: {
        controls: {
          from: { setValue(value: string): void };
          to: { setValue(value: string): void };
        };
      };
      applyFilters(): void;
    };
    component.filters.controls.from.setValue('2026-09-01');
    component.filters.controls.to.setValue('2026-09-30');
    fixture.detectChanges();
    component.applyFilters();

    expect(search).toHaveBeenLastCalledWith({
      from: '2026-09-01',
      to: '2026-09-30',
      page: 0,
      size: 20,
    });

    response.next({ ...populatedPage, page: 0, size: 20, totalElements: 60, totalPages: 3 });
    fixture.detectChanges();
    const paginator = fixture.debugElement.query(By.css('mat-paginator')).componentInstance;
    paginator.page.emit({ pageIndex: 1, pageSize: 50, length: 60 });
    expect(search).toHaveBeenLastCalledWith({
      from: '2026-09-01',
      to: '2026-09-30',
      page: 1,
      size: 50,
    });

    const buttons = Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>,
    );
    const clear = buttons.find((button) => button.textContent?.trim() === 'Limpar') as HTMLButtonElement;
    clear.click();
    expect(search).toHaveBeenLastCalledWith({ page: 0, size: 50 });
  });

  it('should show empty and retry error states', () => {
    response.next({ ...populatedPage, content: [], totalElements: 0, totalPages: 0 });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Nenhuma produção encontrada');

    response.error(new Error('network'));
    fixture.detectChanges();
    const retry = Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>,
    ).find((button) => button.textContent?.trim() === 'Tentar novamente') as HTMLButtonElement;
    retry.click();
    expect(search).toHaveBeenCalledTimes(2);
  });
});
