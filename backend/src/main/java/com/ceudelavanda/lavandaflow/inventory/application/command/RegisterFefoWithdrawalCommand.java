package com.ceudelavanda.lavandaflow.inventory.application.command;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Input for an automatic FEFO withdrawal across eligible batches of one item.
 */
public record RegisterFefoWithdrawalCommand(
    UUID inventoryItemId,
    BigDecimal quantity,
    String reason
) {
}
