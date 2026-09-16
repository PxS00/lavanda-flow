package com.ceudelavanda.lavandaflow.catalog.application;

import java.util.UUID;

/** Supported catalog maintenance fields for an existing inventory item. */
public record UpdateInventoryItemCommand(
    UUID inventoryItemId,
    String name,
    String description,
    boolean active,
    String essenceReference,
    String productionTypeCode
) {
}
