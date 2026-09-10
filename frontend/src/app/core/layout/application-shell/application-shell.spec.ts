import { BreakpointObserver, BreakpointState } from '@angular/cdk/layout';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSidenav } from '@angular/material/sidenav';
import { By } from '@angular/platform-browser';
import { provideRouter, Router } from '@angular/router';
import { BehaviorSubject, of, throwError } from 'rxjs';

import { AuthSessionService } from '../../auth/auth-session.service';
import { ApplicationShell } from './application-shell';

const persistentNavigationQuery = '(min-width: 960px) and (hover: hover) and (pointer: fine)';

describe('ApplicationShell', () => {
  let component: ApplicationShell;
  let fixture: ComponentFixture<ApplicationShell>;
  let logout: ReturnType<typeof vi.fn>;
  let router: Router;
  let persistentNavigationState: BehaviorSubject<BreakpointState>;
  let observe: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    logout = vi.fn(() => of({ kind: 'unauthenticated' }));
    persistentNavigationState = new BehaviorSubject<BreakpointState>({ matches: true, breakpoints: {} });
    observe = vi.fn((query: string | readonly string[]) => {
      if (query !== persistentNavigationQuery) {
        throw new Error(`Unexpected breakpoint query: ${query}`);
      }

      return persistentNavigationState;
    });
    await TestBed.configureTestingModule({
      imports: [ApplicationShell],
      providers: [
        provideRouter([]),
        {
          provide: BreakpointObserver,
          useValue: { observe },
        },
        {
          provide: AuthSessionService,
          useValue: { username: signal('Operadora'), logout },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ApplicationShell);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
    expect(observe).toHaveBeenCalledWith(persistentNavigationQuery);
  });

  it('uses a persistent compact rail for a wide fine-pointer viewport', () => {
    const sidenav = fixture.debugElement.query(By.directive(MatSidenav)).componentInstance as MatSidenav;

    expect(sidenav.mode).toBe('side');
    expect(sidenav.opened).toBe(true);
    expect(fixture.nativeElement.querySelector('mat-sidenav').classList.contains('persistent-navigation')).toBe(true);
    expect(fixture.nativeElement.querySelector('button[aria-label="Abrir navegação"]')).toBeNull();
  });

  it('should render grouped supported destinations without a fake output route', () => {
    const headings = Array.from(
      fixture.nativeElement.querySelectorAll('.nav-group h2'),
      (heading: Element) => heading.textContent?.trim(),
    );
    const links = Array.from(
      fixture.nativeElement.querySelectorAll('.primary-navigation a'),
    ) as HTMLAnchorElement[];

    expect(headings).toEqual(['Visão geral', 'Estoque', 'Produção', 'Cadastros']);
    expect(links.map((link) => [link.textContent?.trim(), link.getAttribute('href')])).toEqual([
      ['Painel', '/dashboard'],
      ['Estoque', '/catalog'],
      ['Entradas', '/receipts'],
      ['Alertas', '/inventory/alerts'],
      ['Produção', '/production/formulas'],
      ['Fornecedores', '/suppliers'],
    ]);
    expect(links.map((link) => link.getAttribute('aria-label'))).toEqual([
      'Painel',
      'Estoque',
      'Entradas',
      'Alertas',
      'Produção',
      'Fornecedores',
    ]);
    expect(links.every((link) => link.querySelector('.nav-link-content .nav-glyph') !== null)).toBe(true);
    expect(fixture.nativeElement.querySelectorAll('.nav-glyph[aria-hidden="true"]')).toHaveLength(6);
    expect(fixture.nativeElement.textContent).not.toContain('Saídas');
    expect(links.some((link) => link.getAttribute('href') === '/outputs')).toBe(false);
  });

  it('should render a replaceable accessible brand slot', () => {
    const brandSlot = fixture.nativeElement.querySelector('.brand-slot') as HTMLAnchorElement;
    const brandLogo = brandSlot.querySelector('.brand-logo') as HTMLImageElement;

    expect(brandSlot.getAttribute('aria-label')).toBe('Lavanda Flow — Painel');
    expect(brandSlot.getAttribute('href')).toBe('/dashboard');
    expect(brandLogo).toBeTruthy();
    expect(brandSlot.querySelector('.compact-brand')?.getAttribute('src')).toBe('/favicon-lf.svg');
    expect(brandSlot.querySelector('.full-brand')?.getAttribute('src')).toBe('/lavanda-flow-logo.svg');
  });

  it('keeps labels and links structurally available for keyboard rail expansion', () => {
    const links = Array.from(fixture.nativeElement.querySelectorAll('.primary-navigation a')) as HTMLAnchorElement[];

    expect(links).toHaveLength(6);
    expect(links.every((link) => link.querySelector('.nav-label')?.textContent?.trim())).toBe(true);
    expect(links.every((link) => link.getAttribute('tabindex') !== '-1')).toBe(true);
    expect(fixture.nativeElement.querySelector('mat-sidenav').classList.contains('persistent-navigation')).toBe(true);
  });

  it('uses an overlay drawer and closes it after navigation for touch or narrow contexts', async () => {
    persistentNavigationState.next({ matches: false, breakpoints: {} });
    fixture.detectChanges();
    await fixture.whenStable();
    const sidenav = fixture.debugElement.query(By.directive(MatSidenav)).componentInstance as MatSidenav;
    const menuButton = fixture.nativeElement.querySelector(
      'button[aria-label="Abrir navegação"]',
    ) as HTMLButtonElement;

    expect(sidenav.mode).toBe('over');
    expect(sidenav.opened).toBe(false);
    expect(fixture.nativeElement.querySelector('mat-sidenav').classList.contains('overlay-navigation')).toBe(true);
    expect(menuButton).toBeTruthy();
    expect(menuButton.getAttribute('aria-label')).toBe('Abrir navegação');
    expect(menuButton.getAttribute('aria-expanded')).toBe('false');

    menuButton.click();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(menuButton.getAttribute('aria-expanded')).toBe('true');
    expect(menuButton.getAttribute('aria-label')).toBe('Fechar navegação');
    expect(menuButton.textContent?.trim()).toBe('');

    (fixture.nativeElement.querySelector('.primary-navigation a') as HTMLAnchorElement).click();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(sidenav.opened).toBe(false);
    expect((fixture.nativeElement.querySelector('.primary-navigation a') as HTMLAnchorElement).getAttribute('href')).toBe(
      '/dashboard',
    );
  });

  it('offers a Portuguese logout action', () => {
    const button = (Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[]).find(
      (element: HTMLButtonElement) => element.textContent?.trim() === 'Sair',
    ) as HTMLButtonElement;

    expect(button).toBeTruthy();
    button.click();

    expect(logout).toHaveBeenCalledTimes(1);
    expect(router.navigateByUrl).toHaveBeenCalledWith('/login');
  });

  it('keeps authenticated state when server logout fails', () => {
    logout.mockReturnValue(throwError(() => new Error('Network failure')));
    const button = (Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[]).find(
      (element: HTMLButtonElement) => element.textContent?.trim() === 'Sair',
    ) as HTMLButtonElement;

    button.click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(
      'Ocorreu um erro inesperado.',
    );
    expect(fixture.nativeElement.textContent).toContain('Operadora');
  });
});
