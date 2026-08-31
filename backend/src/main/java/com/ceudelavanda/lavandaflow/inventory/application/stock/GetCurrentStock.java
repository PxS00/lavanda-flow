package com.ceudelavanda.lavandaflow.inventory.application.stock;

import com.ceudelavanda.lavandaflow.catalog.InventoryItemLookup;
import com.ceudelavanda.lavandaflow.inventory.domain.Batch;
import com.ceudelavanda.lavandaflow.inventory.domain.BatchRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InventoryItemNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;

/**
 * Read-only application use case that returns the current physical stock
 * recorded for one catalog item and its batches.
 *
 * <p>The aggregate quantity includes every batch balance, including expired
 * and zero-balance batches. Zero-balance filtering affects only the returned
 * batch detail. Inactive catalog items remain queryable, and this use case
 * does not interpret expiration status.</p>
 */
@Service
@RequiredArgsConstructor
public class GetCurrentStock {

    private static final Comparator<Batch> BATCH_ORDER = Comparator
        .comparing(Batch::getExpiresAt, Comparator.nullsLast(Comparator.naturalOrder()))
        .thenComparing(Batch::getReceivedAt)
        .thenComparing(Batch::getId);

    private final InventoryItemLookup inventoryItemLookup;
    private final BatchRepository batchRepository;

    /**
     * Retrieves the current stock for an item, including its active state from
     * the catalog public API. Unknown items are rejected, while inactive items
     * remain visible to preserve inventory history.
     *
     * @throws InventoryItemNotFoundException if the item does not exist
     */
    @Transactional(readOnly = true)
    public CurrentStockResult execute(GetCurrentStockQuery query) {
        var inventoryItem = inventoryItemLookup.findById(query.inventoryItemId())
            .orElseThrow(() -> new InventoryItemNotFoundException(query.inventoryItemId()));
        var batches = batchRepository.findByInventoryItemId(query.inventoryItemId());
        var totalCurrentQuantity = batches.stream()
            .map(Batch::getCurrentQuantity)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        var batchResults = batches.stream()
            .filter(batch -> query.includeZeroBalance() || batch.getCurrentQuantity().signum() != 0)
            .sorted(BATCH_ORDER)
            .map(this::toBatchStockResult)
            .toList();

        return new CurrentStockResult(
            inventoryItem.id(),
            inventoryItem.active(),
            totalCurrentQuantity,
            batchResults
        );
    }

    private BatchStockResult toBatchStockResult(Batch batch) {
        return new BatchStockResult(
            batch.getId(),
            batch.getSupplierId(),
            batch.getLotCode(),
            batch.getCurrentQuantity(),
            batch.getReceivedAt(),
            batch.getExpiresAt()
        );
    }
}
