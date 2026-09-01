export interface RegisterStockReceiptRequest {
  readonly inventoryItemId: string;
  readonly supplierId: string | null;
  readonly lotCode: string | null;
  readonly quantity: number;
  readonly receivedAt: string;
  readonly expiresAt: string | null;
  readonly reason: string | null;
}

/** Transport representation returned after one committed stock receipt. */
export interface RegisterStockReceiptDto {
  readonly batchId: string;
  readonly movementId: string;
  readonly inventoryItemId: string;
  readonly supplierId: string | null;
  readonly lotCode: string | null;
  readonly quantity: number;
  readonly receivedAt: string;
  readonly expiresAt: string | null;
  readonly reason: string | null;
  readonly occurredAt: string;
}
