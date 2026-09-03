package com.ceudelavanda.lavandaflow.catalog;

import java.util.UUID;

/** Immutable catalog metadata required by the production module. */
public record ProductionItemReference(
    UUID inventoryItemId,
    UnitOfMeasure unitOfMeasure,
    boolean active,
    String essenceReference,
    String productionTypeCode
) {
}
