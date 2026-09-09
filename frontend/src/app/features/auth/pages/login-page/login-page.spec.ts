import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter, Router } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';

import { AuthSessionService } from '../../../../core/auth/auth-session.service';
import { LoginPage } from './login-page';
import { signal } from '@angular/core';

describe('LoginPage', () => {
  let fixture: ComponentFixture<LoginPage>;
  let login: ReturnType<typeof vi.fn>;
  let refresh: ReturnType<typeof vi.fn>;
  let state: ReturnType<typeof signal>;
  let router: Router;

  beforeEach(async () => {
    state = signal({ kind: 'unauthenticated' });
    login = vi.fn();
    refresh = vi.fn();
    await TestBed.configureTestingModule({
      imports: [LoginPage],
      providers: [
        provideRouter([]),
        {
          provide: AuthSessionService,
          useValue: { state, login, refresh },
        },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(LoginPage);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    fixture.detectChanges();
  });

  it('renders Portuguese labels and credential-safe inputs', () => {
    expect(fixture.nativeElement.querySelector('.login-brand')?.textContent).toContain(
      'Lavanda Flow',
    );
    expect(fixture.nativeElement.textContent).toContain('Entrar no Lavanda Flow');
    expect(input('username').getAttribute('autocomplete')).toBe('username');
    expect(input('password').getAttribute('type')).toBe('password');
    expect(input('password').getAttribute('autocomplete')).toBe('current-password');
  });

  it('validates required fields and the backend username length limit', () => {
    submit();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Informe o usuário.');
    expect(fixture.nativeElement.textContent).toContain('Informe a senha.');

    input('username').value = 'a'.repeat(101);
    input('username').dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Informe no máximo 100 caracteres.');
  });

  it('does not render a submit path while the session bootstrap is pending', () => {
    state.set({ kind: 'loading' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('form')).toBeNull();
    expect(fixture.nativeElement.querySelector('[role="status"]')?.textContent).toContain(
      'Preparando acesso seguro...',
    );
  });

  it('disables duplicate login submissions while authentication is pending', () => {
    const response = new Subject<{ kind: 'authenticated'; username: string }>();
    login.mockReturnValue(response);
    setCredentials();

    submit();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('button[type="submit"]')?.hasAttribute('disabled')).toBe(true);
    response.complete();
  });

  it('navigates only after the post-login session refresh confirms authentication', () => {
    login.mockReturnValue(of({ kind: 'authenticated', username: 'Operator' }));
    setCredentials();

    submit();

    expect(login).toHaveBeenCalledWith({ username: ' operator ', password: 'secret' });
    expect(router.navigateByUrl).toHaveBeenCalledWith('/dashboard');
    expect(input('password').value).toBe('');
  });

  it('shows generic localized invalid-credential feedback and clears the password', () => {
    login.mockReturnValue(
      throwError(() => new HttpErrorResponse({
        status: 401,
        error: {
          timestamp: '2026-09-07T00:00:00Z',
          status: 401,
          error: 'Unauthorized',
          code: 'AUTHENTICATION_FAILED',
          message: 'Authentication failed',
          path: '/api/v1/auth/login',
          details: null,
        },
      })),
    );
    setCredentials();

    submit();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(
      'Usuário ou senha incorretos.',
    );
    expect(input('password').value).toBe('');
  });

  it('redirects to dashboard when bootstrap retry restores an authenticated session', () => {
    state.set({ kind: 'error', error: { kind: 'network', message: 'Network error' } });
    refresh.mockImplementation(() => {
      state.set({ kind: 'authenticated', username: 'Operator' });
      return of({ kind: 'authenticated', username: 'Operator' });
    });

    fixture.detectChanges();

    const retryButton = Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>,
    ).find((button) => button.textContent?.includes('Tentar novamente'));

    expect(retryButton).toBeDefined();

    retryButton?.click();
    fixture.detectChanges();

    expect(refresh).toHaveBeenCalledTimes(1);
    expect(router.navigateByUrl).toHaveBeenCalledWith('/dashboard');
    expect(fixture.nativeElement.querySelector('form')).toBeNull();
  });

  it('restores a usable login form when bootstrap retry confirms an unauthenticated session', () => {
    state.set({ kind: 'error', error: { kind: 'network', message: 'Network error' } });
    refresh.mockImplementation(() => {
      state.set({ kind: 'unauthenticated' });
      return of({ kind: 'unauthenticated' });
    });

    fixture.detectChanges();

    const retryButton = Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>,
    ).find((button) => button.textContent?.includes('Tentar novamente'));

    expect(retryButton).toBeDefined();

    retryButton?.click();
    fixture.detectChanges();

    expect(refresh).toHaveBeenCalledTimes(1);
    expect(router.navigateByUrl).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('form')).not.toBeNull();

    setCredentials();

    expect(
      fixture.nativeElement.querySelector('button[type="submit"]')?.hasAttribute('disabled'),
    ).toBe(false);
  });

  function input(controlName: string): HTMLInputElement {
    return fixture.nativeElement.querySelector(`[formcontrolname="${controlName}"]`);
  }

  function setCredentials(): void {
    input('username').value = ' operator ';
    input('username').dispatchEvent(new Event('input'));
    input('password').value = 'secret';
    input('password').dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function submit(): void {
    fixture.nativeElement.querySelector('form')?.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }
});
