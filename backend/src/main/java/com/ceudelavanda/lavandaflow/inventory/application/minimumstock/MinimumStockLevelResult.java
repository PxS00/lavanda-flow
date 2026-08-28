package com.ceudelavanda.lavandaflow.inventory.application.minimumstock;

import java.math.BigDecimal;
import java.util.UUID;

public record MinimumStockLevelResult(UUID inventoryItemId, BigDecimal minimumQuantity) {
}
