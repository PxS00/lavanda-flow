package com.ceudelavanda.lavandaflow.inventory.application;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Read port for bulk available-stock aggregation at a business date.
 *
 * <p>Availability contains positive batch balances with either no expiration date or an expiration date
 * strictly after {@code asOfDate}. A missing item balance is represented by the caller as zero.</p>
 */
public interface AvailableStockQuery {

    List<AvailableStockBalance> findAvailableStockByInventoryItemIds(
        Collection<UUID> inventoryItemIds,
        LocalDate asOfDate
    );
}
