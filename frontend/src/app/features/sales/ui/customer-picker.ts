import { Component, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { Subject, catchError, map, of, startWith, switchMap, tap } from 'rxjs';
import { CustomerDto } from '../../customers/data-access/customer.dto';
import { CustomerApiService } from '../../customers/data-access/customer-api.service';
import { UiError } from '../../../core/http/ui-error';
import { mapHttpError } from '../../../core/http/map-http-error';
import { ErrorState } from '../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../shared/ui/loading-state/loading-state';

@Component({
  selector: 'app-customer-picker',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    ErrorState,
    LoadingState,
  ],
  template: `
    <mat-form-field appearance="outline"
      ><mat-label>Buscar cliente ativo</mat-label>
      <input matInput [formControl]="searchText" [readonly]="disabled()" />
    </mat-form-field>
    <button mat-button type="button" [disabled]="disabled()" (click)="search()">Buscar</button>
    @if (loading()) {
      <app-loading-state message="Buscando..." />
    }
    @if (error(); as failure) {
      <app-error-state [error]="failure" />
      <button mat-button type="button" [disabled]="disabled()" (click)="retry()">
        Tentar novamente
      </button>
    }
    @if (!loading() && !error()) {
      @if (items().length === 0) {
        <p>Nenhum resultado elegível nesta página. Tente outra busca ou página.</p>
      }
      <ul aria-label="Resultados da busca">
        @for (item of items(); track item.id) {
          <li>
            <button mat-button type="button" [disabled]="disabled()" (click)="selected.emit(item)">
              {{ item.name }}
            </button>
          </li>
        }
      </ul>
      <mat-paginator
        aria-label="Páginas da busca"
        [length]="total()"
        [pageIndex]="pageIndex()"
        [pageSize]="20"
        [disabled]="disabled()"
        (page)="changePage($event)"
      />
    }
  `,
  styles:
    'ul { list-style: none; padding: 0; } button { max-width: 100%; white-space: normal; height: auto; min-height: 2.75rem; overflow-wrap: anywhere; }',
})
export class CustomerPicker {
  private readonly api = inject(CustomerApiService);
  private readonly requests = new Subject<{ q: string; page: number }>();
  private query = { q: '', page: 0 };
  readonly disabled = input(false);
  readonly selected = output<CustomerDto>();
  readonly searchText = new FormControl('', { nonNullable: true });
  protected readonly items = signal<readonly CustomerDto[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<UiError | null>(null);
  protected readonly total = signal(0);
  protected readonly pageIndex = signal(0);

  constructor() {
    this.requests
      .pipe(
        startWith(this.query),
        tap(() => {
          this.loading.set(true);
          this.error.set(null);
        }),
        switchMap((query) =>
          this.api.search({ q: query.q, active: true, page: query.page, size: 20 }).pipe(
            map((page) => ({ page, error: null })),
            catchError((error: unknown) => of({ page: null, error: mapHttpError(error) })),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((result) => {
        this.loading.set(false);
        this.error.set(result.error);
        if (result.page) {
          const page = result.page;
          this.items.set(page.content.filter((item) => item.active));
          this.total.set(page.totalElements);
          this.pageIndex.set(page.page);
        }
      });
  }
  protected search(): void {
    this.query = { q: this.searchText.value.trim(), page: 0 };
    this.retry();
  }
  protected changePage(event: PageEvent): void {
    this.query = { ...this.query, page: event.pageIndex };
    this.retry();
  }
  protected retry(): void {
    this.requests.next(this.query);
  }
}
