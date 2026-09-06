export interface InventoryDashboardDto {
  readonly asOfDate: string;
  readonly expirationWindowDays: number;
  readonly activeItemCount: number;
  readonly lowStockItemCount: number;
  readonly outOfStockItemCount: number;
  readonly expiringSoonBatchCount: number;
  readonly expiredBatchCount: number;
}
