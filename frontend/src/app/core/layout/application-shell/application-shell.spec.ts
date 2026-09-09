import { BreakpointObserver, BreakpointState } from '@angular/cdk/layout';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { signal } from '@angular/core';
import { BehaviorSubject, of, throwError } from 'rxjs';

import { AuthSessionService } from '../../auth/auth-session.service';
import { ApplicationShell } from './application-shell';

describe('ApplicationShell', () => {
  let component: ApplicationShell;
  let fixture: ComponentFixture<ApplicationShell>;
  let logout: ReturnType<typeof vi.fn>;
  let router: Router;
  let breakpointState: BehaviorSubject<BreakpointState>;

  beforeEach(async () => {
    logout = vi.fn(() => of({ kind: 'unauthenticated' }));
    breakpointState = new BehaviorSubject<BreakpointState>({ matches: false, breakpoints: {} });
    await TestBed.configureTestingModule({
      imports: [ApplicationShell],
      providers: [
        provideRouter([]),
        {
          provide: BreakpointObserver,
          useValue: { observe: () => breakpointState },
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
    expect(fixture.nativeElement.textContent).not.toContain('Saídas');
    expect(links.some((link) => link.getAttribute('href') === '/outputs')).toBe(false);
  });

  it('should render a replaceable accessible brand slot', () => {
    const brandSlot = fixture.nativeElement.querySelector('.brand-slot') as HTMLAnchorElement;

    expect(brandSlot.getAttribute('aria-label')).toBe('Lavanda Flow — Painel');
    expect(brandSlot.getAttribute('href')).toBe('/dashboard');
    expect(brandSlot.querySelector('.brand-mark')?.textContent).toContain('LF');
  });

  it('should expose an accessible navigation trigger on narrow screens', async () => {
    breakpointState.next({ matches: true, breakpoints: {} });
    fixture.detectChanges();
    const menuButton = fixture.nativeElement.querySelector(
      'button[aria-label="Abrir navegação"]',
    ) as HTMLButtonElement;

    expect(menuButton).toBeTruthy();
    expect(menuButton.getAttribute('aria-expanded')).toBe('false');

    menuButton.click();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(menuButton.getAttribute('aria-expanded')).toBe('true');
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
  });
});
