package com.ceudelavanda.lavandaflow.inventory.domain;

import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Represents a physical inventory batch received for one catalog item.
 *
 * <p>A batch materializes its current balance. The catalog item and optional
 * supplier are referenced by their public identifiers so this aggregate does
 * not depend directly on aggregates owned by other modules.</p>
 */
@Getter
public class Batch {

    private final UUID id;
    private final UUID inventoryItemId;
    private final UUID supplierId;
    private final String lotCode;
    private final BigDecimal initialQuantity;
    private BigDecimal currentQuantity;
    private final LocalDate receivedAt;
    private final LocalDate expiresAt;

    public Batch(
        UUID id,
        UUID inventoryItemId,
        UUID supplierId,
        String lotCode,
        BigDecimal initialQuantity,
        BigDecimal currentQuantity,
        LocalDate receivedAt,
        LocalDate expiresAt
    ) {
        this.id = requireNonNull(id, "id");
        this.inventoryItemId = requireNonNull(inventoryItemId, "inventoryItemId");
        this.supplierId = supplierId;
        this.lotCode = normalizeOptional(lotCode);
        this.initialQuantity = requirePositive(initialQuantity, "initialQuantity");
        this.currentQuantity = requireNonNegative(currentQuantity);
        this.receivedAt = requireNonNull(receivedAt, "receivedAt");
        this.expiresAt = expiresAt;
    }

    /**
     * Creates a newly received batch with its current balance equal to the
     * received quantity.
     */
    public static Batch create(
        UUID inventoryItemId,
        UUID supplierId,
        String lotCode,
        BigDecimal initialQuantity,
        LocalDate receivedAt,
        LocalDate expiresAt
    ) {
        return new Batch(
            UUID.randomUUID(),
            inventoryItemId,
            supplierId,
            lotCode,
            initialQuantity,
            initialQuantity,
            receivedAt,
            expiresAt
        );
    }

    /**
     * Adds a positive quantity to the current batch balance.
     *
     * @param quantity quantity to add
     * @throws IllegalArgumentException if the quantity is null or not positive
     */
    public void addQuantity(BigDecimal quantity) {
        var validatedQuantity = requirePositive(quantity, "quantity");
        this.currentQuantity = this.currentQuantity.add(validatedQuantity);
    }

    private static BigDecimal requirePositive(BigDecimal value, String field){
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(field + " must be greater than zero");
        }
        return value;
    }

    private static BigDecimal requireNonNegative(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException("currentQuantity must not be negative");
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
