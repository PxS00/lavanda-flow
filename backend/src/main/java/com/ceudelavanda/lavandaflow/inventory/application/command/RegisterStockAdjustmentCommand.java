package com.ceudelavanda.lavandaflow.inventory.application.command;

import java.math.BigDecimal;
import java.util.UUID;

public record RegisterStockAdjustmentCommand(

    UUID batchId,
    BigDecimal quantity,
    String reason
) {
}
