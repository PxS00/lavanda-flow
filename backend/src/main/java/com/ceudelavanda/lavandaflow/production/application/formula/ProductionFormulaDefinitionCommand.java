package com.ceudelavanda.lavandaflow.production.application.formula;

import com.ceudelavanda.lavandaflow.production.domain.ProductionFormulaKind;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProductionFormulaDefinitionCommand(
    UUID outputInventoryItemId,
    BigDecimal outputQuantity,
    List<ProductionFormulaIngredientCommand> ingredients,
    ProductionFormulaKind kind
) {

    public ProductionFormulaDefinitionCommand {
        kind = kind == null ? ProductionFormulaKind.STANDARD : kind;
    }

    public ProductionFormulaDefinitionCommand(
        UUID outputInventoryItemId,
        BigDecimal outputQuantity,
        List<ProductionFormulaIngredientCommand> ingredients
    ) {
        this(outputInventoryItemId, outputQuantity, ingredients, ProductionFormulaKind.STANDARD);
    }

    public ProductionFormulaKind effectiveKind() {
        return kind;
    }
}
