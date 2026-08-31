package com.ceudelavanda.lavandaflow.inventory.infrastructure.web.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** HTTP contract for registering one new stock receipt. */
public record RegisterStockReceiptRequest(
    @NotNull UUID inventoryItemId,
    UUID supplierId,
    @Size(max = 255) String lotCode,
    @NotNull @Positive @Digits(integer = 13, fraction = 6) BigDecimal quantity,
    @NotNull LocalDate receivedAt,
    LocalDate expiresAt,
    @Size(max = 255) String reason
) {
}
