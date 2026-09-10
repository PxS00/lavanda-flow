import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatListModule } from '@angular/material/list';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { map } from 'rxjs';

import { AuthSessionService } from '../../auth/auth-session.service';
import { localizeUiError } from '../../http/localize-ui-error';
import { mapHttpError } from '../../http/map-http-error';

const persistentNavigationQuery = '(min-width: 960px) and (hover: hover) and (pointer: fine)';

@Component({
  selector: 'app-application-shell',
  imports: [
    MatButtonModule,
    MatListModule,
    MatSidenavModule,
    MatToolbarModule,
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
  ],
  templateUrl: './application-shell.html',
  styleUrl: './application-shell.scss',
})
export class ApplicationShell {
  private readonly breakpointObserver = inject(BreakpointObserver);
  private readonly authSession = inject(AuthSessionService);
  private readonly router = inject(Router);

  protected readonly usesOverlayNavigation = toSignal(
    this.breakpointObserver
      .observe(persistentNavigationQuery)
      .pipe(map((result) => !result.matches)),
    { initialValue: true },
  );
  protected readonly username = this.authSession.username;
  protected readonly loggingOut = signal(false);
  protected readonly logoutError = signal<string | null>(null);

  protected logout(): void {
    if (this.loggingOut()) {
      return;
    }

    this.loggingOut.set(true);
    this.logoutError.set(null);
    this.authSession.logout().subscribe({
      next: () => {
        this.loggingOut.set(false);
        void this.router.navigateByUrl('/login');
      },
      error: (error: unknown) => {
        this.loggingOut.set(false);
        this.logoutError.set(localizeUiError(mapHttpError(error)).message);
      },
    });
  }
}
