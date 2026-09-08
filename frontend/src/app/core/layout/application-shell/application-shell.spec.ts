import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';

import { AuthSessionService } from '../../auth/auth-session.service';
import { ApplicationShell } from './application-shell';

describe('ApplicationShell', () => {
  let component: ApplicationShell;
  let fixture: ComponentFixture<ApplicationShell>;
  let logout: ReturnType<typeof vi.fn>;
  let router: Router;

  beforeEach(async () => {
    logout = vi.fn(() => of({ kind: 'unauthenticated' }));
    await TestBed.configureTestingModule({
      imports: [ApplicationShell],
      providers: [
        provideRouter([]),
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

  it('should link Inventory navigation to the catalog workspace', () => {
    const links = Array.from(fixture.nativeElement.querySelectorAll('a')) as HTMLAnchorElement[];
    const inventoryLink = links.find((link) => link.textContent?.trim() === 'Estoque');

    expect(inventoryLink?.getAttribute('href')).toBe('/catalog');
  });

  it('should link Suppliers navigation to the supplier workspace', () => {
    const links = Array.from(fixture.nativeElement.querySelectorAll('a')) as HTMLAnchorElement[];
    const suppliersLink = links.find((link) => link.textContent?.trim() === 'Fornecedores');

    expect(suppliersLink?.getAttribute('href')).toBe('/suppliers');
  });

  it('should link Receipts navigation to the stock receipt workflow', () => {
    const links = Array.from(fixture.nativeElement.querySelectorAll('a')) as HTMLAnchorElement[];
    const receiptsLink = links.find((link) => link.textContent?.trim() === 'Entradas');

    expect(receiptsLink?.getAttribute('href')).toBe('/receipts');
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
