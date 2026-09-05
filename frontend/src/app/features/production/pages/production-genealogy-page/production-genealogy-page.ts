import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subject, catchError, map, of, switchMap, tap } from 'rxjs';

import { mapHttpError } from '../../../../core/http/map-http-error';
import { UiError } from '../../../../core/http/ui-error';
import { formatLocalDate } from '../../../../core/i18n/local-date';
import { EmptyState } from '../../../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../../shared/ui/loading-state/loading-state';
import { inventoryItemUnitLabel } from '../../../catalog/inventory-item-display';
import { ProductionGenealogyApiService } from '../../data-access/production-genealogy-api.service';
import {
  BatchGenealogyDto,
  GenealogyDirection,
} from '../../data-access/production-genealogy.dto';
import {
  GenealogyEdgeTree,
  genealogyOriginLabel,
} from '../../ui/genealogy-edge-tree/genealogy-edge-tree';

type GenealogyState =
  | { readonly kind: 'loading' }
  | { readonly kind: 'loaded'; readonly genealogy: BatchGenealogyDto }
  | { readonly kind: 'error'; readonly error: UiError };

interface GenealogyRequest {
  readonly batchId: string;
  readonly direction: GenealogyDirection;
}

@Component({
  selector: 'app-production-genealogy-page',
  imports: [
    EmptyState,
    ErrorState,
    GenealogyEdgeTree,
    LoadingState,
    MatButtonModule,
    MatCardModule,
    RouterLink,
  ],
  templateUrl: './production-genealogy-page.html',
  styleUrl: './production-genealogy-page.scss',
})
export class ProductionGenealogyPage {
  private readonly route = inject(ActivatedRoute);
  private readonly genealogyApi = inject(ProductionGenealogyApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly requests = new Subject<GenealogyRequest>();
  private readonly batchId = signal<string | null>(null);

  protected readonly direction = signal<GenealogyDirection>('BOTH');
  protected readonly state = signal<GenealogyState>({ kind: 'loading' });
  protected readonly formatLocalDate = formatLocalDate;
  protected readonly unitLabel = inventoryItemUnitLabel;
  protected readonly originLabel = genealogyOriginLabel;

  constructor() {
    this.requests
      .pipe(
        tap(() => this.state.set({ kind: 'loading' })),
        switchMap(({ batchId, direction }) =>
          this.genealogyApi.getBatchGenealogy(batchId, direction).pipe(
            map((genealogy): GenealogyState => ({ kind: 'loaded', genealogy })),
            catchError((error: unknown) =>
              of<GenealogyState>({ kind: 'error', error: mapHttpError(error) }),
            ),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((state) => this.state.set(state));

    this.route.paramMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => this.loadBatch(params.get('batchId')));
  }

  protected changeDirection(event: Event): void {
    const direction = (event.currentTarget as HTMLSelectElement).value as GenealogyDirection;
    this.direction.set(direction);
    this.request(direction);
  }

  protected retry(): void {
    this.request(this.direction());
  }

  protected includesUpstream(direction: GenealogyDirection): boolean {
    return direction === 'UPSTREAM' || direction === 'BOTH';
  }

  protected includesDownstream(direction: GenealogyDirection): boolean {
    return direction === 'DOWNSTREAM' || direction === 'BOTH';
  }

  private loadBatch(batchId: string | null): void {
    const normalizedBatchId = batchId?.trim() ?? '';
    if (normalizedBatchId.length === 0) {
      this.batchId.set(null);
      this.state.set({
        kind: 'error',
        error: { kind: 'validation', message: 'Missing batch ID.' },
      });
      return;
    }

    this.batchId.set(normalizedBatchId);
    this.direction.set('BOTH');
    this.requests.next({ batchId: normalizedBatchId, direction: 'BOTH' });
  }

  private request(direction: GenealogyDirection): void {
    const batchId = this.batchId();
    if (batchId !== null) {
      this.requests.next({ batchId, direction });
    }
  }
}
