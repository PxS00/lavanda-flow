package com.ceudelavanda.lavandaflow.inventory;

import java.time.LocalDate;
import java.util.UUID;

/** Immutable inventory-owned batch display facts for cross-module read models. */
public record BatchDetails(
    UUID id,
    UUID inventoryItemId,
    UUID supplierId,
    String lotCode,
    LocalDate receivedAt,
    LocalDate expiresAt
) {
}
