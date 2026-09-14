package com.ceudelavanda.lavandaflow.production.application.formula;

import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.production.domain.ProductionFormula;
import com.ceudelavanda.lavandaflow.production.domain.ProductionFormulaKind;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProductionFormulaResult(
    UUID id,
    UUID outputInventoryItemId,
    BigDecimal outputQuantity,
    UnitOfMeasure outputUnitOfMeasure,
    List<IngredientResult> ingredients,
    ProductionFormulaKind kind
) {

    public ProductionFormulaResult {
        ingredients = List.copyOf(ingredients);
    }

    public ProductionFormulaResult(
        UUID id,
        UUID outputInventoryItemId,
        BigDecimal outputQuantity,
        UnitOfMeasure outputUnitOfMeasure,
        List<IngredientResult> ingredients
    ) {
        this(id, outputInventoryItemId, outputQuantity, outputUnitOfMeasure, ingredients, ProductionFormulaKind.STANDARD);
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
                .toList(),
            formula.getKind()
        );
    }

    public record IngredientResult(
        UUID inventoryItemId,
        BigDecimal quantity,
        UnitOfMeasure unitOfMeasure
    ) {
    }
}
