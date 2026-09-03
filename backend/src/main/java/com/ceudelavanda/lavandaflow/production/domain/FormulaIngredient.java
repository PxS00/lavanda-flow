package com.ceudelavanda.lavandaflow.production.domain;

import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.production.domain.exception.InvalidProductionFormulaException;

import java.math.BigDecimal;
import java.util.UUID;

/** One catalog-item requirement in a production formula. */
public record FormulaIngredient(
    UUID inventoryItemId,
    BigDecimal quantity,
    UnitOfMeasure unitOfMeasure
) {

    public FormulaIngredient {
        if (inventoryItemId == null) {
            throw new InvalidProductionFormulaException("ingredient.inventoryItemId", "Ingredient inventory item must not be null");
        }
        ProductionFormula.requireSupportedPositiveQuantity(quantity, "ingredient.quantity");
        if (unitOfMeasure == null) {
            throw new InvalidProductionFormulaException("ingredient.unitOfMeasure", "Ingredient unit of measure must not be null");
        }
    }
}
