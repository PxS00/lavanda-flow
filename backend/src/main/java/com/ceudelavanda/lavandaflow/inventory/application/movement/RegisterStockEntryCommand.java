package com.ceudelavanda.lavandaflow.inventory.application.movement;

import java.math.BigDecimal;
import java.util.UUID;

public record RegisterStockEntryCommand(
    UUID batchId,
    BigDecimal quantity,
    String reason
) {
}
