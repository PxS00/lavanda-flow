package com.ceudelavanda.lavandaflow.production.infrastructure.web.response;

import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.production.application.genealogy.GenealogyBatch;
import com.ceudelavanda.lavandaflow.production.application.genealogy.GenealogyBatchOrigin;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Inventory-owned batch display facts embedded in production genealogy")
public record GenealogyBatchResponse(
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
    public static GenealogyBatchResponse from(GenealogyBatch batch) {
        return new GenealogyBatchResponse(
            batch.batchId(),
            batch.origin(),
            batch.inventoryItemId(),
            batch.itemName(),
            batch.itemCategory(),
            batch.unitOfMeasure(),
            batch.supplierId(),
            batch.lotCode(),
            batch.receivedAt(),
            batch.expiresAt()
        );
    }
}
