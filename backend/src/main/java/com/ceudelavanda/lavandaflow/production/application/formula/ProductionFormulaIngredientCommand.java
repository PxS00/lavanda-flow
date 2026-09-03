package com.ceudelavanda.lavandaflow.production.application.formula;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductionFormulaIngredientCommand(
    UUID inventoryItemId,
    BigDecimal quantity
) {
}
