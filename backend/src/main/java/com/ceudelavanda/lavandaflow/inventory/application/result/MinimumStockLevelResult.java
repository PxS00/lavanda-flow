package com.ceudelavanda.lavandaflow.inventory.application.result;

import java.math.BigDecimal;
import java.util.UUID;

public record MinimumStockLevelResult(UUID inventoryItemId, BigDecimal minimumQuantity) {
}
