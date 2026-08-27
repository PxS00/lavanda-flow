package com.ceudelavanda.lavandaflow.inventory.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Represents one immutable, auditable change to the stock of a batch.
 *
 * <p>The movement type defines whether the quantity increases or decreases
 * stock. Quantities are always positive to avoid encoding direction twice.</p>
 */
public record StockMovement(
    UUID id,
    UUID batchId,
    MovementType type,
    BigDecimal quantity,
    String reason,
    Instant occurredAt
) {

    private static final int MAX_REASON_LENGTH = 255;

    public StockMovement(
        UUID id,
        UUID batchId,
        MovementType type,
        BigDecimal quantity,
        String reason,
        Instant occurredAt
    ) {
        this.id = requireNonNull(id, "id");
        this.batchId = requireNonNull(batchId, "batchId");
        this.type = requireNonNull(type, "type");
        this.quantity = StockQuantityRules.requirePositive(quantity, "quantity");
        this.reason = validateReason(normalizeOptional(reason));
        this.occurredAt = requireNonNull(occurredAt, "occurredAt");
    }

    /**
     * Creates a stock movement at the supplied audit instant.
     */
    public static StockMovement create(
        UUID batchId,
        MovementType type,
        BigDecimal quantity,
        String reason,
        Instant occurredAt
    ) {
        return new StockMovement(
            UUID.randomUUID(),
            batchId,
            type,
            quantity,
            reason,
            occurredAt
        );
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }

        var normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String validateReason(String value) {
        if (value != null && value.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException(
                "reason must not exceed " + MAX_REASON_LENGTH + " characters"
            );
        }

        return value;
    }

    private static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }

        return value;
    }
}
