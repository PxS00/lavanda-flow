package com.ceudelavanda.lavandaflow.inventory.domain;

import com.ceudelavanda.lavandaflow.inventory.domain.exception.InvalidMinimumStockQuantityException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Configured minimum available quantity for one inventory item.
 *
 * <p>Quantities are positive and normalized to the canonical six-decimal inventory scale. Values that
 * would require rounding are rejected rather than silently changed.</p>
 */
public class MinimumStockLevel {

    private final UUID inventoryItemId;
    private BigDecimal minimumQuantity;

    public MinimumStockLevel(UUID inventoryItemId, BigDecimal minimumQuantity) {
        if (inventoryItemId == null) {
            throw new IllegalArgumentException("inventoryItemId must not be null");
        }

        this.inventoryItemId = inventoryItemId;
        this.minimumQuantity = normalize(minimumQuantity);
    }

    public UUID getInventoryItemId() {
        return inventoryItemId;
    }

    public BigDecimal getMinimumQuantity() {
        return minimumQuantity;
    }

    public void changeMinimumQuantity(BigDecimal minimumQuantity) {
        this.minimumQuantity = normalize(minimumQuantity);
    }

    private static BigDecimal normalize(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new InvalidMinimumStockQuantityException(quantity);
        }

        try {
            return quantity.setScale(6, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new InvalidMinimumStockQuantityException(quantity);
        }
    }
}
