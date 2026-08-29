package com.ceudelavanda.lavandaflow.inventory.application.batch;

import com.ceudelavanda.lavandaflow.catalog.InventoryItemLookup;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InventoryItemNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Retrieves the operational batch inventory for one catalog item.
 *
 * <p>Status is derived at read time from the materialized balance and the
 * application business date. Zero-balance batches are classified as
 * {@link BatchOperationalStatus#ZERO_BALANCE} regardless of expiration.
 * Positive-balance batches expiring on or before the business date are
 * {@link BatchOperationalStatus#EXPIRED}; all remaining batches are
 * {@link BatchOperationalStatus#AVAILABLE}.</p>
 */
@Service
@RequiredArgsConstructor
public class GetBatchInventory {

    private final InventoryItemLookup inventoryItemLookup;
    private final BatchInventoryQuery batchInventoryQuery;
    private final Clock clock;

    /**
     * Retrieves deterministically ordered batch inventory for an existing item.
     *
     * @throws InventoryItemNotFoundException if the catalog item does not exist
     */
    @Transactional(readOnly = true)
    public BatchInventoryResult execute(java.util.UUID inventoryItemId) {
        inventoryItemLookup.findById(inventoryItemId)
            .orElseThrow(() -> new InventoryItemNotFoundException(inventoryItemId));

        var asOfDate = LocalDate.now(clock);
        var batches = batchInventoryQuery.findByInventoryItemId(inventoryItemId).stream()
            .map(batch -> toResult(batch, asOfDate))
            .toList();

        return new BatchInventoryResult(inventoryItemId, asOfDate, batches);
    }

    private static BatchInventoryEntryResult toResult(BatchInventoryRecord batch, LocalDate asOfDate) {
        return new BatchInventoryEntryResult(
            batch.batchId(),
            batch.inventoryItemId(),
            batch.supplierId(),
            batch.lotCode(),
            batch.initialQuantity(),
            batch.currentQuantity(),
            batch.receivedAt(),
            batch.expiresAt(),
            statusOf(batch, asOfDate)
        );
    }

    private static BatchOperationalStatus statusOf(BatchInventoryRecord batch, LocalDate asOfDate) {
        if (batch.currentQuantity().signum() == 0) {
            return BatchOperationalStatus.ZERO_BALANCE;
        }
        if (batch.expiresAt() != null && !batch.expiresAt().isAfter(asOfDate)) {
            return BatchOperationalStatus.EXPIRED;
        }
        return BatchOperationalStatus.AVAILABLE;
    }
}
