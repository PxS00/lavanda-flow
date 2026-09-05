import { DecimalPipe } from '@angular/common';
import { Component, input } from '@angular/core';
import { MatCardModule } from '@angular/material/card';

import { formatLocalDate } from '../../../../core/i18n/local-date';
import { inventoryItemUnitLabel } from '../../../catalog/inventory-item-display';
import {
  GenealogyBatchDto,
  GenealogyBatchOrigin,
  GenealogyEdgeDto,
} from '../../data-access/production-genealogy.dto';

@Component({
  selector: 'app-genealogy-edge-tree',
  imports: [DecimalPipe, MatCardModule],
  templateUrl: './genealogy-edge-tree.html',
  styleUrl: './genealogy-edge-tree.scss',
})
export class GenealogyEdgeTree {
  readonly edges = input.required<readonly GenealogyEdgeDto[]>();

  protected readonly formatLocalDate = formatLocalDate;
  protected readonly unitLabel = inventoryItemUnitLabel;
  protected readonly originLabel = genealogyOriginLabel;
  protected readonly batchLabel = genealogyBatchLabel;
}

export function genealogyOriginLabel(origin: GenealogyBatchOrigin): string {
  return origin === 'INTERNALLY_PRODUCED' ? 'Produção interna' : 'Externo ou sem produção associada';
}

export function genealogyBatchLabel(batch: GenealogyBatchDto): string {
  return batch.lotCode === null ? batch.itemName : `${batch.itemName} · lote ${batch.lotCode}`;
}
