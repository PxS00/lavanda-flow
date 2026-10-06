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
    Instant occurredAt,
    com.ceudelavanda.lavandaflow.inventory.StockAuditReference reference
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
        this(id, batchId, type, quantity, reason, occurredAt, null);
    }

    public StockMovement {
        id = requireNonNull(id, "id");
        batchId = requireNonNull(batchId, "batchId");
        type = requireNonNull(type, "type");
        quantity = StockQuantityRules.requirePositive(quantity, "quantity");
        reason = validateReason(normalizeOptional(reason));
        occurredAt = requireNonNull(occurredAt, "occurredAt");
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
