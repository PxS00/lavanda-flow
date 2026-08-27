package com.ceudelavanda.lavandaflow.inventory.application.result;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Expiration alert details for one positive-balance inventory batch.
 *
 * @param inventoryItemId catalog item identifier associated with the batch
 * @param batchId batch identifier
 * @param lotCode nullable supplier/manufacturer lot reference
 * @param currentQuantity current materialized balance of the batch
 * @param expiresAt batch expiration date
 * @param daysUntilExpiration signed day distance from the evaluation date; zero is already expired
 * @param status expiration classification for the evaluation date and alert window
 */
public record ExpirationAlertEntryResult(
    UUID inventoryItemId,
    UUID batchId,
    String lotCode,
    BigDecimal currentQuantity,
    LocalDate expiresAt,
    long daysUntilExpiration,
    ExpirationAlertStatus status
) {
}
