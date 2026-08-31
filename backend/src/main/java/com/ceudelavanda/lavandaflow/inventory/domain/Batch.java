package com.ceudelavanda.lavandaflow.inventory.domain;

import com.ceudelavanda.lavandaflow.inventory.domain.exception.InsufficientStockException;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InvalidBatchDataException;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InvalidStockAdjustmentException;
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

    private static final int MAX_LOT_CODE_LENGTH = 255;

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
        this.lotCode = validateLotCode(normalizeOptional(lotCode));
        this.initialQuantity = StockQuantityRules.requirePositive(
            initialQuantity,
            "initialQuantity"
        );
        this.currentQuantity = StockQuantityRules.requireNonNegative(
            currentQuantity,
            "currentQuantity"
        );
        this.receivedAt = requireNonNull(receivedAt, "receivedAt");
        this.expiresAt = validateExpiration(expiresAt, this.receivedAt);
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
     * @throws IllegalArgumentException if the quantity or resulting balance cannot be represented exactly, or the quantity is not positive
     */
    public void addQuantity(BigDecimal quantity) {
        var validatedQuantity = StockQuantityRules.requirePositive(quantity, "quantity");
        this.currentQuantity = StockQuantityRules.requireNonNegative(
            this.currentQuantity.add(validatedQuantity),
            "currentQuantity"
        );
    }

    /**
     * Removes a positive quantity from the current batch balance.
     *
     * @param quantity quantity to remove
     * @throws IllegalArgumentException if the quantity cannot be represented exactly or is not positive
     * @throws InsufficientStockException if the quantity exceeds the available balance
     */
    public void removeQuantity(BigDecimal quantity) {
        var validatedQuantity = StockQuantityRules.requirePositive(quantity, "quantity");

        if (currentQuantity.compareTo(validatedQuantity) < 0) {
            throw new InsufficientStockException(id, validatedQuantity, currentQuantity);
        }

        this.currentQuantity = this.currentQuantity.subtract(validatedQuantity);
    }

    /**
     * Applies a signed adjustment to the current batch balance.
     *
     * <p>Positive values increase the balance and negative values decrease it.
     * The adjustment must not reduce the balance below zero.</p>
     *
     * @param adjustment signed quantity to apply
     * @throws IllegalArgumentException if the adjustment cannot be represented exactly or is null
     * @throws InvalidStockAdjustmentException if the adjustment is zero
     * @throws InsufficientStockException if a negative adjustment exceeds the available balance
     */
    public void adjustQuantity(BigDecimal adjustment) {
        if (adjustment == null) {
            throw new IllegalArgumentException("adjustment must not be null");
        }
        if (adjustment.signum() == 0) {
            throw new InvalidStockAdjustmentException();
        }

        var validatedAdjustment = StockQuantityRules.requireNonZero(
            adjustment,
            "adjustment"
        );

        if (validatedAdjustment.signum() > 0) {
            addQuantity(validatedAdjustment);
            return;
        }

        removeQuantity(validatedAdjustment.abs());
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }

        var normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String validateLotCode(String value) {
        if (value != null && value.length() > MAX_LOT_CODE_LENGTH) {
            throw new InvalidBatchDataException(
                "lotCode",
                "lotCode must not exceed " + MAX_LOT_CODE_LENGTH + " characters"
            );
        }
        return value;
    }

    private static LocalDate validateExpiration(LocalDate expiresAt, LocalDate receivedAt) {
        if (expiresAt != null && expiresAt.isBefore(receivedAt)) {
            throw new InvalidBatchDataException(
                "expiresAt",
                "expiresAt must not be before receivedAt"
            );
        }
        return expiresAt;
    }

    private static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }

        return value;
    }

}
