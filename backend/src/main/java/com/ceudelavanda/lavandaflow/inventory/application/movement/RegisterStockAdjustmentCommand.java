package com.ceudelavanda.lavandaflow.inventory.application.movement;

import java.math.BigDecimal;
import java.util.UUID;

public record RegisterStockAdjustmentCommand(
    UUID batchId,
    BigDecimal quantity,
    String reason
) {
}
