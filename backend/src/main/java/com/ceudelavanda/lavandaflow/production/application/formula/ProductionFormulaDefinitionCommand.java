package com.ceudelavanda.lavandaflow.production.application.formula;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProductionFormulaDefinitionCommand(
    UUID outputInventoryItemId,
    BigDecimal outputQuantity,
    List<ProductionFormulaIngredientCommand> ingredients
) {
}
