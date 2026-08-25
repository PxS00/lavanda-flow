package com.ceudelavanda.lavandaflow.inventory.domain;

import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Represents one immutable, auditable change to the stock of a batch.
 *
 * <p>The movement type defines whether the quantity increases or decreases
 * stock. Quantities are always positive to avoid encoding direction twice.</p>
 */
@Getter
public class StockMovement {

    private final UUID id;
    private final UUID batchId;
    private final MovementType type;
    private final BigDecimal quantity;
    private final String reason;
    private final Instant occurredAt;

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
        this.quantity = requirePositive(quantity);
        this.reason = normalizeOptional(reason);
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

    private static BigDecimal requirePositive(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException("quantity must be greater than zero");
        }

        return value;
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }

        var normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }

        return value;
    }
}
