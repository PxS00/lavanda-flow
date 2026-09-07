package com.ceudelavanda.lavandaflow.production.domain;

import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.production.domain.exception.InvalidProductionFormulaException;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Defines the current production requirements for one output catalog item.
 *
 * <p>A formula describes item requirements only. Concrete source batches and actual
 * consumptions belong to a production execution, not to this aggregate.</p>
 */
@Getter
public class ProductionFormula {

    private final UUID id;
    private UUID outputInventoryItemId;
    private BigDecimal outputQuantity;
    private UnitOfMeasure outputUnitOfMeasure;
    private List<FormulaIngredient> ingredients;

    public ProductionFormula(
        UUID id,
        UUID outputInventoryItemId,
        BigDecimal outputQuantity,
        UnitOfMeasure outputUnitOfMeasure,
        List<FormulaIngredient> ingredients
    ) {
        if (id == null) {
            throw new InvalidProductionFormulaException("id", "Formula id must not be null");
        }
        this.id = id;
        replaceDefinition(outputInventoryItemId, outputQuantity, outputUnitOfMeasure, ingredients);
    }

    public static ProductionFormula create(
        UUID outputInventoryItemId,
        BigDecimal outputQuantity,
        UnitOfMeasure outputUnitOfMeasure,
        List<FormulaIngredient> ingredients
    ) {
        return new ProductionFormula(
            UUID.randomUUID(),
            outputInventoryItemId,
            outputQuantity,
            outputUnitOfMeasure,
            ingredients
        );
    }

    /** Replaces the editable current definition while preserving formula identity. */
    public void replaceDefinition(
        UUID outputInventoryItemId,
        BigDecimal outputQuantity,
        UnitOfMeasure outputUnitOfMeasure,
        List<FormulaIngredient> ingredients
    ) {
        if (outputInventoryItemId == null) {
            throw new InvalidProductionFormulaException("outputInventoryItemId", "Output inventory item must not be null");
        }
        requireSupportedPositiveQuantity(outputQuantity, "outputQuantity");
        if (outputUnitOfMeasure == null) {
            throw new InvalidProductionFormulaException("outputUnitOfMeasure", "Output unit of measure must not be null");
        }

        var validatedIngredients = requireIngredients(ingredients);
        this.outputInventoryItemId = outputInventoryItemId;
        this.outputQuantity = outputQuantity;
        this.outputUnitOfMeasure = outputUnitOfMeasure;
        this.ingredients = validatedIngredients;
    }

    static void requireSupportedPositiveQuantity(BigDecimal quantity, String field) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new InvalidProductionFormulaException(field, "Quantity must be greater than zero");
        }

        var fractionDigits = Math.max(quantity.scale(), 0);
        var integerDigits = Math.max(quantity.precision() - quantity.scale(), 0);
        if (integerDigits > 13 || fractionDigits > 6) {
            throw new InvalidProductionFormulaException(
                field,
                "Quantity must have at most 13 integer digits and 6 fractional digits"
            );
        }
    }

    private static List<FormulaIngredient> requireIngredients(List<FormulaIngredient> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) {
            throw new InvalidProductionFormulaException("ingredients", "Formula must contain at least one ingredient");
        }

        var itemIds = new HashSet<UUID>();
        for (var ingredient : ingredients) {
            if (ingredient == null) {
                throw new InvalidProductionFormulaException("ingredients", "Formula ingredients must not contain null entries");
            }
            if (!itemIds.add(ingredient.inventoryItemId())) {
                throw new InvalidProductionFormulaException(
                    "ingredients",
                    "Formula must not contain the same ingredient inventory item more than once"
                );
            }
        }
        return List.copyOf(ingredients);
    }
}
