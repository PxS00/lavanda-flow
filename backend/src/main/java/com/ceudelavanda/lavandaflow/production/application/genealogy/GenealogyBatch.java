package com.ceudelavanda.lavandaflow.production.application.genealogy;

import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;

import java.time.LocalDate;
import java.util.UUID;

/** Immutable batch and item display data embedded in a genealogy read model. */
public record GenealogyBatch(
    UUID batchId,
    GenealogyBatchOrigin origin,
    UUID inventoryItemId,
    String itemName,
    String itemCategory,
    UnitOfMeasure unitOfMeasure,
    UUID supplierId,
    String lotCode,
    LocalDate receivedAt,
    LocalDate expiresAt
) {
}
