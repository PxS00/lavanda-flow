export type ProductionLotCodeMode = 'GENERATED' | 'MANUAL';

/** Exact concrete source-batch quantity submitted for one completed production execution. */
export interface ProductionSourceAllocationRequest {
  readonly batchId: string;
  readonly quantity: string;
}

/** Request payload accepted by the completed production registration endpoint. */
export interface RegisterProductionRequest {
  readonly formulaId: string;
  readonly outputQuantity: string;
  readonly sourceAllocations: readonly ProductionSourceAllocationRequest[];
  readonly productionDate: string;
  readonly outputReceivedAt: string;
  readonly outputExpiresAt: string | null;
  readonly lotCodeMode: ProductionLotCodeMode;
  readonly manualLotCode: string | null;
}

/** Backend-confirmed source consumption persisted for a production execution. */
export interface ProductionConsumptionDto {
  readonly sourceBatchId: string;
  readonly sourceInventoryItemId: string;
  readonly movementId: string;
  readonly quantity: string;
}

/** Authoritative completed production execution returned by the backend. */
export interface ProductionExecutionDto {
  readonly executionId: string;
  readonly formulaId: string;
  readonly outputInventoryItemId: string;
  readonly outputBatchId: string;
  readonly outputQuantity: string;
  readonly lotCode: string;
  readonly lotCodeMode: ProductionLotCodeMode;
  readonly productionDate: string;
  readonly outputReceivedAt: string;
  readonly outputExpiresAt: string | null;
  readonly completedAt: string;
  readonly consumptions: readonly ProductionConsumptionDto[];
}

export interface ProductionExecutionHistoryQuery {
  readonly from?: string;
  readonly to?: string;
  readonly page: number;
  readonly size: number;
}

export interface ProductionExecutionHistoryEntryDto {
  readonly executionId: string;
  readonly formulaId: string;
  readonly outputInventoryItemId: string;
  readonly outputItemName: string;
  readonly outputUnitOfMeasure: import('../../catalog/data-access/inventory-item.dto').InventoryItemUnitOfMeasure;
  readonly outputBatchId: string;
  readonly outputQuantity: string;
  readonly lotCode: string;
  readonly lotCodeMode: ProductionLotCodeMode;
  readonly productionDate: string;
  readonly completedAt: string;
}

export interface ProductionExecutionHistoryPageDto {
  readonly content: readonly ProductionExecutionHistoryEntryDto[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
}

export interface ProductionExecutionDetailsConsumptionDto {
  readonly sourceBatchId: string;
  readonly sourceInventoryItemId: string;
  readonly sourceItemName: string;
  readonly sourceUnitOfMeasure: import('../../catalog/data-access/inventory-item.dto').InventoryItemUnitOfMeasure;
  readonly sourceLotCode: string | null;
  readonly movementId: string;
  readonly quantity: string;
}

export interface ProductionExecutionDetailsDto {
  readonly executionId: string;
  readonly formulaId: string;
  readonly outputInventoryItemId: string;
  readonly outputItemName: string;
  readonly outputUnitOfMeasure: import('../../catalog/data-access/inventory-item.dto').InventoryItemUnitOfMeasure;
  readonly outputBatchId: string;
  readonly outputQuantity: string;
  readonly lotCode: string;
  readonly lotCodeMode: ProductionLotCodeMode;
  readonly productionDate: string;
  readonly outputReceivedAt: string;
  readonly outputExpiresAt: string | null;
  readonly completedAt: string;
  readonly consumptions: readonly ProductionExecutionDetailsConsumptionDto[];
}
