import { InventoryUnitOfMeasure } from './inventory-operations.dto';

export type ExpirationAlertStatus = 'EXPIRED' | 'EXPIRING_SOON';

/** Transport representation of backend-authoritative low-stock alerts. */
export interface LowStockAlertEntryDto {
  readonly inventoryItemId: string;
  readonly name: string;
  readonly unitOfMeasure: InventoryUnitOfMeasure;
  readonly availableQuantity: number;
  readonly minimumQuantity: number;
  readonly deficitQuantity: number;
}

/** Transport representation of the low-stock alert query. */
export interface LowStockAlertsDto {
  readonly asOfDate: string;
  readonly alerts: readonly LowStockAlertEntryDto[];
}

/** Transport representation of one backend-classified expiration alert. */
export interface ExpirationAlertEntryDto {
  readonly inventoryItemId: string;
  readonly batchId: string;
  readonly lotCode: string | null;
  readonly currentQuantity: number;
  readonly expiresAt: string;
  readonly daysUntilExpiration: number;
  readonly status: ExpirationAlertStatus;
}

/** Transport representation of the expiration alert query. */
export interface ExpirationAlertsDto {
  readonly asOfDate: string;
  readonly windowDays: number;
  readonly alerts: readonly ExpirationAlertEntryDto[];
}
