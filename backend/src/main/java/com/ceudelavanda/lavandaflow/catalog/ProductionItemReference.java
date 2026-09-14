package com.ceudelavanda.lavandaflow.catalog;

import java.util.UUID;

/** Immutable catalog metadata required by the production module. */
public record ProductionItemReference(
    UUID inventoryItemId,
    UnitOfMeasure unitOfMeasure,
    boolean active,
    String essenceReference,
    String productionTypeCode,
    String category
) {

    /** Backward-compatible constructor for callers that do not need category metadata. */
    public ProductionItemReference(
        UUID inventoryItemId,
        UnitOfMeasure unitOfMeasure,
        boolean active,
        String essenceReference,
        String productionTypeCode
    ) {
        this(inventoryItemId, unitOfMeasure, active, essenceReference, productionTypeCode, null);
    }
}
