import { InventoryMovementType } from './inventory-operations.dto';

/** Exact-decimal maintenance request for one selected inventory batch. */
export interface StockMaintenanceRequest {
  readonly quantity: string;
  readonly reason: string;
}

/** Backend-confirmed immutable movement created by a stock-maintenance operation. */
export interface StockMaintenanceMovementDto {
  readonly movementId: string;
  readonly batchId: string;
  readonly type: InventoryMovementType;
  readonly quantity: string;
  readonly resultingBalance: string;
  readonly reason: string;
  readonly occurredAt: string;
}
