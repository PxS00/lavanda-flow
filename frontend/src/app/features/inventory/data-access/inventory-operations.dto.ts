export type InventoryUnitOfMeasure = 'MILLILITER' | 'LITER' | 'GRAM' | 'KILOGRAM' | 'UNIT';

export type BatchOperationalStatus = 'AVAILABLE' | 'EXPIRED' | 'ZERO_BALANCE';

export type InventoryMovementType =
  | 'ENTRY'
  | 'CONSUMPTION'
  | 'ADJUSTMENT_IN'
  | 'ADJUSTMENT_OUT'
  | 'LOSS'
  | 'EXPIRED_DISPOSAL';

export interface InventoryItemOverviewDto {
  readonly inventoryItemId: string;
  readonly name: string;
  readonly category: string;
  readonly unitOfMeasure: InventoryUnitOfMeasure;
  readonly active: boolean;
  readonly asOfDate: string;
  readonly expirationWindowDays: number;
  readonly totalCurrentQuantity: number;
  readonly availableQuantity: number;
  readonly minimumQuantity: number | null;
  readonly lowStock: boolean;
  readonly outOfStock: boolean;
  readonly nonZeroBatchCount: number;
  readonly nearestExpiration: string | null;
  readonly expiredBatchCount: number;
  readonly expiringSoonBatchCount: number;
}

export interface BatchInventoryEntryDto {
  readonly batchId: string;
  readonly inventoryItemId: string;
  readonly supplierId: string | null;
  readonly lotCode: string | null;
  readonly initialQuantity: number;
  readonly currentQuantity: number;
  readonly receivedAt: string;
  readonly expiresAt: string | null;
  readonly status: BatchOperationalStatus;
}

export interface BatchInventoryDto {
  readonly inventoryItemId: string;
  readonly asOfDate: string;
  readonly batches: readonly BatchInventoryEntryDto[];
}

export interface MinimumStockLevelDto {
  readonly inventoryItemId: string;
  readonly minimumQuantity: number;
}

export interface ConfigureMinimumStockLevelRequest {
  readonly minimumQuantity: number;
}

export interface MovementHistoryEntryDto {
  readonly movementId: string;
  readonly inventoryItemId: string;
  readonly inventoryItemName: string;
  readonly unitOfMeasure: InventoryUnitOfMeasure;
  readonly inventoryItemActive: boolean;
  readonly batchId: string;
  readonly lotCode: string | null;
  readonly type: InventoryMovementType;
  readonly quantity: number;
  readonly reason: string | null;
  readonly occurredAt: string;
}

export interface MovementHistoryPageDto {
  readonly content: readonly MovementHistoryEntryDto[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
}

export interface InventoryItemMovementHistoryQuery {
  readonly inventoryItemId: string;
  readonly page: number;
  readonly size: number;
}
