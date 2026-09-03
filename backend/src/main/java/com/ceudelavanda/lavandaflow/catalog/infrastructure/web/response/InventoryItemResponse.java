package com.ceudelavanda.lavandaflow.catalog.infrastructure.web.response;

import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.catalog.application.InventoryItemResult;
import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Inventory catalog item")
public record InventoryItemResponse(
    UUID id,
    String name,
    String description,
    Category category,
    UnitOfMeasure unitOfMeasure,
    boolean active,
    String essenceReference,
    String productionTypeCode
) {
    public static InventoryItemResponse from(InventoryItemResult result) {
        return new InventoryItemResponse(
            result.id(),
            result.name(),
            result.description(),
            result.category(),
            result.unitOfMeasure(),
            result.active(),
            result.essenceReference(),
            result.productionTypeCode()
        );
    }
}
