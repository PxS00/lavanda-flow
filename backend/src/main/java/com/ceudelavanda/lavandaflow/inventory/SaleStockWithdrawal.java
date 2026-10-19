package com.ceudelavanda.lavandaflow.inventory;

import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Inventory-owned FEFO withdrawal of finished products in the caller's PostgreSQL transaction.
 * Requires an existing transaction. Locks items in UUID order before batches, validates active
 * finished-product UNIT/mL identities and eligible stock, and creates immutable audited movements.
 * Any failure rolls back the caller's transaction; no automatic retry or reservation exists.
 */
public interface SaleStockWithdrawal {
    List<Result> withdraw(List<Line> lines);
    record Line(UUID itemId, BigDecimal quantity, StockAuditReference reference) {}
    /** Product labels are read under the item lock for confirmation-time snapshots. */
    record Result(UUID lineId, String itemName, UnitOfMeasure unitOfMeasure, List<Allocation> allocations) {
        public Result { allocations = List.copyOf(allocations); }
    }
    record Allocation(UUID batchId, UUID movementId, BigDecimal quantity) {}
}
