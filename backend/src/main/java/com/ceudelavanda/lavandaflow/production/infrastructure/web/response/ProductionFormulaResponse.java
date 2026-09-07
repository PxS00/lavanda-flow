package com.ceudelavanda.lavandaflow.production.infrastructure.web.response;

import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.production.application.formula.ProductionFormulaResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Schema(description = "Current production formula definition")
public record ProductionFormulaResponse(
    UUID id,
    UUID outputInventoryItemId,
    BigDecimal outputQuantity,
    UnitOfMeasure outputUnitOfMeasure,
    List<IngredientResponse> ingredients
) {

    public ProductionFormulaResponse {
        ingredients = List.copyOf(ingredients);
    }

    public static ProductionFormulaResponse from(ProductionFormulaResult result) {
        return new ProductionFormulaResponse(
            result.id(),
            result.outputInventoryItemId(),
            result.outputQuantity(),
            result.outputUnitOfMeasure(),
            result.ingredients().stream()
                .map(ingredient -> new IngredientResponse(
                    ingredient.inventoryItemId(),
                    ingredient.quantity(),
                    ingredient.unitOfMeasure()
                ))
                .toList()
        );
    }

    public record IngredientResponse(
        UUID inventoryItemId,
        BigDecimal quantity,
        UnitOfMeasure unitOfMeasure
    ) {
    }
}
