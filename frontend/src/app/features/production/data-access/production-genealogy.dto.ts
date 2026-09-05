import {
  InventoryItemCategory,
  InventoryItemUnitOfMeasure,
} from '../../catalog/data-access/inventory-item.dto';

export type GenealogyDirection = 'UPSTREAM' | 'DOWNSTREAM' | 'BOTH';

export type GenealogyBatchOrigin = 'INTERNALLY_PRODUCED' | 'EXTERNAL_OR_NON_PRODUCED';

export interface GenealogyBatchDto {
  readonly batchId: string;
  readonly origin: GenealogyBatchOrigin;
  readonly inventoryItemId: string;
  readonly itemName: string;
  readonly itemCategory: InventoryItemCategory;
  readonly unitOfMeasure: InventoryItemUnitOfMeasure;
  readonly supplierId: string | null;
  readonly lotCode: string | null;
  readonly receivedAt: string;
  readonly expiresAt: string | null;
}

export interface GenealogyEdgeDto {
  readonly executionId: string;
  readonly formulaId: string;
  readonly productionDate: string;
  readonly completedAt: string;
  readonly consumedQuantity: number;
  readonly sourceBatch: GenealogyBatchDto;
  readonly outputBatch: GenealogyBatchDto;
  readonly next: readonly GenealogyEdgeDto[];
}

export interface BatchGenealogyDto {
  readonly direction: GenealogyDirection;
  readonly rootBatch: GenealogyBatchDto;
  readonly upstream: readonly GenealogyEdgeDto[];
  readonly downstream: readonly GenealogyEdgeDto[];
}
