export type ProductionLotCodeMode = 'GENERATED' | 'MANUAL';

/** Exact concrete source-batch quantity submitted for one completed production execution. */
export interface ProductionSourceAllocationRequest {
  readonly batchId: string;
  readonly quantity: number;
}

/** Request payload accepted by the completed production registration endpoint. */
export interface RegisterProductionRequest {
  readonly formulaId: string;
  readonly outputQuantity: number;
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
  readonly quantity: number;
}

/** Authoritative completed production execution returned by the backend. */
export interface ProductionExecutionDto {
  readonly executionId: string;
  readonly formulaId: string;
  readonly outputInventoryItemId: string;
  readonly outputBatchId: string;
  readonly outputQuantity: number;
  readonly lotCode: string;
  readonly lotCodeMode: ProductionLotCodeMode;
  readonly productionDate: string;
  readonly outputReceivedAt: string;
  readonly outputExpiresAt: string | null;
  readonly completedAt: string;
  readonly consumptions: readonly ProductionConsumptionDto[];
}
