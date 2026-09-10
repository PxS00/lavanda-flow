package com.ceudelavanda.lavandaflow.inventory.application.movement;

import java.math.BigDecimal;
import java.util.UUID;

/** Input for disposing expired stock from one selected batch. */
public record RegisterExpiredStockDisposalCommand(UUID batchId, BigDecimal quantity, String reason) {
}
