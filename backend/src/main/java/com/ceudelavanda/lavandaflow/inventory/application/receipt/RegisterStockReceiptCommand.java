package com.ceudelavanda.lavandaflow.inventory.application.receipt;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Input for registering one new stock receipt and its initial movement. */
public record RegisterStockReceiptCommand(
    UUID inventoryItemId,
    UUID supplierId,
    String lotCode,
    BigDecimal quantity,
    LocalDate receivedAt,
    LocalDate expiresAt,
    String reason
) {
}
