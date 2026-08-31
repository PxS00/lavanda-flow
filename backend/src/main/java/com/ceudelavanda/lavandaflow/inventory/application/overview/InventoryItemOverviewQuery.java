package com.ceudelavanda.lavandaflow.inventory.application.overview;

import java.time.LocalDate;
import java.util.UUID;

/** Read port for inventory-owned metrics used by the item overview. */
public interface InventoryItemOverviewQuery {

    InventoryItemOverviewMetrics findMetrics(
        UUID inventoryItemId,
        LocalDate asOfDate,
        LocalDate expirationCutoff
    );
}
