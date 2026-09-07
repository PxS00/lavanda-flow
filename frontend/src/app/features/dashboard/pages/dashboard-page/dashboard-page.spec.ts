import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject } from 'rxjs';

import { InventoryDashboardApiService } from '../../data-access/inventory-dashboard-api.service';
import { InventoryDashboardDto } from '../../data-access/inventory-dashboard.dto';
import { DashboardPage } from './dashboard-page';

describe('DashboardPage', () => {
  const summary: InventoryDashboardDto = {
    asOfDate: '2026-09-01',
    expirationWindowDays: 30,
    activeItemCount: 12,
    lowStockItemCount: 3,
    outOfStockItemCount: 2,
    expiringSoonBatchCount: 4,
    expiredBatchCount: 1,
  };
  let fixture: ComponentFixture<DashboardPage>;
  let response: Subject<InventoryDashboardDto>;
  let getDashboard: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    response = new Subject<InventoryDashboardDto>();
    getDashboard = vi.fn(() => response);

    await TestBed.configureTestingModule({
      imports: [DashboardPage],
      providers: [
        provideRouter([]),
        { provide: InventoryDashboardApiService, useValue: { getDashboard } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardPage);
    fixture.detectChanges();
  });

  it('should show initial loading and render all five backend counters with context', () => {
    expect(fixture.nativeElement.querySelector('[role="status"]')?.textContent).toContain(
      'Carregando painel operacional...',
    );

    response.next(summary);
    fixture.detectChanges();

    expect(metrics()).toEqual([
      ['Itens ativos', '12'],
      ['Estoque baixo', '3'],
      ['Sem estoque', '2'],
      ['Lotes próximos do vencimento', '4'],
      ['Lotes vencidos', '1'],
    ]);
    expect(fixture.nativeElement.textContent).toContain('Dados de 01/09/2026');
    expect(fixture.nativeElement.textContent).toContain('janela de próximos vencimentos: 30 dias');
  });

  it('should keep every zero metric visible as a successful operational state', () => {
    response.next({
      ...summary,
      activeItemCount: 0,
      lowStockItemCount: 0,
      outOfStockItemCount: 0,
      expiringSoonBatchCount: 0,
      expiredBatchCount: 0,
    });
    fixture.detectChanges();

    expect(metrics()).toHaveLength(5);
    expect(metrics().map(([, value]) => value)).toEqual(['0', '0', '0', '0', '0']);
    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeNull();
  });

  it('should render mapped failures through the shared error state', () => {
    response.error(new HttpErrorResponse({ status: 0, statusText: 'Network Error' }));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(
      'Não foi possível conectar ao servidor.',
    );
  });

  it('should explicitly refresh and render the replacement summary', () => {
    const refreshedResponse = new Subject<InventoryDashboardDto>();
    response.next(summary);
    fixture.detectChanges();
    getDashboard.mockReturnValue(refreshedResponse);

    findButton('Atualizar painel').click();
    fixture.detectChanges();

    expect(getDashboard).toHaveBeenCalledTimes(2);
    expect(fixture.nativeElement.querySelector('[role="status"]')).not.toBeNull();

    refreshedResponse.next({ ...summary, activeItemCount: 18 });
    fixture.detectChanges();
    expect(metrics()[0]).toEqual(['Itens ativos', '18']);
  });

  it('should prevent an older response from replacing the latest refresh', () => {
    const latestResponse = new Subject<InventoryDashboardDto>();
    getDashboard.mockReturnValue(latestResponse);

    findButton('Atualizar painel').click();
    fixture.detectChanges();
    expect(response.observed).toBe(false);

    response.next({ ...summary, activeItemCount: 99 });
    latestResponse.next({ ...summary, activeItemCount: 21 });
    fixture.detectChanges();

    expect(metrics()[0]).toEqual(['Itens ativos', '21']);
    expect(fixture.nativeElement.textContent).not.toContain('99');
  });

  it('should expose only the supported metric destinations', () => {
    response.next(summary);
    fixture.detectChanges();

    const links = Array.from(
      fixture.nativeElement.querySelectorAll('.metric-link'),
    ) as HTMLAnchorElement[];
    expect(links.map((link) => link.getAttribute('href'))).toEqual([
      '/catalog',
      '/inventory/alerts',
      '/inventory/alerts',
      '/inventory/alerts',
    ]);
    expect(links.map((link) => link.getAttribute('aria-label'))).toEqual([
      'Ver itens ativos no catálogo',
      'Ver alertas de estoque baixo',
      'Ver alertas de lotes próximos do vencimento',
      'Ver alertas de lotes vencidos',
    ]);

    const labels = Array.from(fixture.nativeElement.querySelectorAll('.metric-label')) as Element[];
    const outOfStockLabel = labels.find((label) => label.textContent?.trim() === 'Sem estoque');
    expect(outOfStockLabel?.closest('a')).toBeNull();
  });

  function metrics(): string[][] {
    const cards = Array.from(fixture.nativeElement.querySelectorAll('.metric-card')) as Element[];
    return cards.map((card) => [
      card.querySelector('.metric-label')?.textContent?.trim() ?? '',
      card.querySelector('.metric-value')?.textContent?.trim() ?? '',
    ]);
  }

  function findButton(label: string): HTMLButtonElement {
    const buttons = Array.from(
      fixture.nativeElement.querySelectorAll('button'),
    ) as HTMLButtonElement[];
    return buttons.find((button) => button.textContent?.includes(label)) as HTMLButtonElement;
  }
});
