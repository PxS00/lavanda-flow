import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { RouterLink } from '@angular/router';
import { catchError, map, of, Subject, switchMap, tap } from 'rxjs';

import { mapHttpError } from '../../../../core/http/map-http-error';
import { UiError } from '../../../../core/http/ui-error';
import { formatLocalDate } from '../../../../core/i18n/local-date';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../../shared/ui/loading-state/loading-state';
import { InventoryDashboardApiService } from '../../data-access/inventory-dashboard-api.service';
import { InventoryDashboardDto } from '../../data-access/inventory-dashboard.dto';

type DashboardState =
  | { readonly kind: 'loading' }
  | { readonly kind: 'loaded'; readonly data: InventoryDashboardDto }
  | { readonly kind: 'error'; readonly error: UiError };

@Component({
  selector: 'app-dashboard-page',
  imports: [ErrorState, LoadingState, MatButtonModule, MatCardModule, RouterLink],
  templateUrl: './dashboard-page.html',
  styleUrl: './dashboard-page.scss',
})
export class DashboardPage {
  private readonly dashboardApi = inject(InventoryDashboardApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly requests = new Subject<void>();

  protected readonly state = signal<DashboardState>({ kind: 'loading' });
  protected readonly formatLocalDate = formatLocalDate;

  constructor() {
    this.requests
      .pipe(
        tap(() => this.state.set({ kind: 'loading' })),
        switchMap(() =>
          this.dashboardApi.getDashboard().pipe(
            map((data): DashboardState => ({ kind: 'loaded', data })),
            catchError((error: unknown) =>
              of<DashboardState>({ kind: 'error', error: mapHttpError(error) }),
            ),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((state) => this.state.set(state));

    this.requests.next();
  }

  protected refresh(): void {
    this.requests.next();
  }
}
