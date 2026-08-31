package com.ceudelavanda.lavandaflow.catalog;

import java.util.UUID;

/**
 * Richer immutable catalog view for read-model consumers.
 *
 * <p>Category is exposed by its stable external name so other modules do not
 * depend on the catalog module's internal domain enum.</p>
 */
public record InventoryItemDetails(
    UUID id,
    String name,
    String category,
    UnitOfMeasure unitOfMeasure,
    boolean active
) {
}
