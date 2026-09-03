package com.ceudelavanda.lavandaflow.production.application.formula;

import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.production.domain.ProductionFormula;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProductionFormulaResult(
    UUID id,
    UUID outputInventoryItemId,
    BigDecimal outputQuantity,
    UnitOfMeasure outputUnitOfMeasure,
    List<IngredientResult> ingredients
) {

    public ProductionFormulaResult {
        ingredients = List.copyOf(ingredients);
    }

    public static ProductionFormulaResult from(ProductionFormula formula) {
        return new ProductionFormulaResult(
            formula.getId(),
            formula.getOutputInventoryItemId(),
            formula.getOutputQuantity(),
            formula.getOutputUnitOfMeasure(),
            formula.getIngredients().stream()
                .map(ingredient -> new IngredientResult(
                    ingredient.inventoryItemId(),
                    ingredient.quantity(),
                    ingredient.unitOfMeasure()
                ))
                .toList()
        );
    }

    public record IngredientResult(
        UUID inventoryItemId,
        BigDecimal quantity,
        UnitOfMeasure unitOfMeasure
    ) {
    }
}
