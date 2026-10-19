package com.ceudelavanda.lavandaflow.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Public accepted-return writer. Requires the caller transaction and prior source-allocation authorization.
 * Inventory locks item then batch, validates current saleability/expiry, and atomically credits the
 * original batch with a new ADJUSTMENT_IN. Zero balance is valid; undated batches are not.
 * Caller owns idempotency; inventory owns all balance and movement effects.
 */
public interface SaleStockReturn {
    Result credit(UUID itemId, UUID batchId, BigDecimal quantity, String reason, StockAuditReference reference);
    record Result(UUID movementId, Instant occurredAt) {}
}
