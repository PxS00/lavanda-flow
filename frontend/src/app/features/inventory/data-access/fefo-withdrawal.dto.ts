/** Request for a backend-authoritative automatic FEFO withdrawal. */
export interface RegisterFefoWithdrawalRequest {
  readonly quantity: number;
  readonly reason: string | null;
}

/** One committed batch allocation returned by an automatic FEFO withdrawal. */
export interface FefoWithdrawalAllocationDto {
  readonly batchId: string;
  readonly movementId: string;
  readonly quantity: number;
}

/** Transport representation returned after a committed automatic FEFO withdrawal. */
export interface RegisterFefoWithdrawalDto {
  readonly inventoryItemId: string;
  readonly requestedQuantity: number;
  readonly allocatedQuantity: number;
  readonly allocations: readonly FefoWithdrawalAllocationDto[];
}
