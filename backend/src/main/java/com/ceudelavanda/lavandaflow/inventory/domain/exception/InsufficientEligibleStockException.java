package com.ceudelavanda.lavandaflow.inventory.domain.exception;

import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Raised when a FEFO withdrawal cannot be fully satisfied by eligible batches.
 */
@Getter
public final class InsufficientEligibleStockException extends DomainException {

    private final UUID inventoryItemId;
    private final BigDecimal requestedQuantity;
    private final BigDecimal availableQuantity;

    public InsufficientEligibleStockException(
        UUID inventoryItemId,
        BigDecimal requestedQuantity,
        BigDecimal availableQuantity
    ) {
        super(
            "INSUFFICIENT_ELIGIBLE_STOCK",
            "Insufficient eligible stock for inventory item " + inventoryItemId,
            ErrorType.BUSINESS_RULE,
            Map.of(
                "inventoryItemId", inventoryItemId.toString(),
                "requestedQuantity", requestedQuantity.toPlainString(),
                "availableQuantity", availableQuantity.toPlainString()
            )
        );
        this.inventoryItemId = inventoryItemId;
        this.requestedQuantity = requestedQuantity;
        this.availableQuantity = availableQuantity;
    }
}
