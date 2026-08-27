package com.ceudelavanda.lavandaflow.catalog;

import java.util.UUID;

/**
 * Immutable public view of the catalog state needed by other modules.
 */
public record InventoryItemSnapshot(
    UUID id,
    String name,
    UnitOfMeasure unitOfMeasure,
    boolean active
) {
}
