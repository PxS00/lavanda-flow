package com.ceudelavanda.lavandaflow.inventory.application.result;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Current materialized balance and identifying data for one inventory batch.
 *
 * <p>The expiration date is returned as recorded without expiration-state
 * interpretation.</p>
 */
public record BatchStockResult(
    UUID batchId,
    UUID supplierId,
    String lotCode,
    BigDecimal currentQuantity,
    LocalDate receivedAt,
    LocalDate expiresAt
) {
}
