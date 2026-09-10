package com.ceudelavanda.lavandaflow.inventory.application.movement;

import java.math.BigDecimal;
import java.util.UUID;

/** Input for recording a physical stock loss from one selected batch. */
public record RegisterStockLossCommand(UUID batchId, BigDecimal quantity, String reason) {
}
