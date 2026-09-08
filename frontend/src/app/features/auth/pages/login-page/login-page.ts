import { Component, inject, signal } from '@angular/core';
import { ReactiveFormsModule, NonNullableFormBuilder, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';

import { AuthSessionService } from '../../../../core/auth/auth-session.service';
import { localizeUiError } from '../../../../core/http/localize-ui-error';
import { mapHttpError } from '../../../../core/http/map-http-error';

@Component({
  selector: 'app-login-page',
  imports: [
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    ReactiveFormsModule,
  ],
  templateUrl: './login-page.html',
  styleUrl: './login-page.scss',
})
export class LoginPage {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly authSession = inject(AuthSessionService);
  private readonly router = inject(Router);

  protected readonly form = this.formBuilder.group({
    username: ['', [Validators.required, Validators.maxLength(100)]],
    password: ['', Validators.required],
  });
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly sessionState = this.authSession.state;

  protected retrySessionBootstrap(): void {
    this.errorMessage.set(null);

    this.authSession.refresh().subscribe({
      next: (state) => {
        if (state.kind === 'authenticated') {
          void this.router.navigateByUrl('/dashboard');
        }
      },
    });
  }

  protected submit(): void {
    if (this.form.invalid || this.submitting() || this.sessionState().kind !== 'unauthenticated') {
      this.form.markAllAsTouched();
      return;
    }

    const { username, password } = this.form.getRawValue();
    this.submitting.set(true);
    this.errorMessage.set(null);
    this.authSession
      .login({ username, password })
      .pipe(
        finalize(() => {
          this.submitting.set(false);
          this.form.controls.password.reset();
        }),
      )
      .subscribe({
        next: (state) => {
          if (state.kind === 'authenticated') {
            void this.router.navigateByUrl('/dashboard');
            return;
          }

          this.errorMessage.set('Não foi possível confirmar a sessão. Tente novamente.');
        },
        error: (error: unknown) => this.errorMessage.set(localizeUiError(mapHttpError(error)).message),
      });
  }
}
