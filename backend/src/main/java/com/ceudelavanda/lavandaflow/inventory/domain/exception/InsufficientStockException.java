package com.ceudelavanda.lavandaflow.inventory.domain.exception;

import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Raised when a stock operation would reduce a batch balance below zero.
 */
@Getter
public final class InsufficientStockException extends DomainException {

    private final UUID batchId;
    private final BigDecimal requestedQuantity;
    private final BigDecimal availableQuantity;

    public InsufficientStockException(
        UUID batchId,
        BigDecimal requestedQuantity,
        BigDecimal availableQuantity
    ) {
        super(
            "INSUFFICIENT_STOCK",
            "Insufficient stock for batch " + batchId,
            ErrorType.BUSINESS_RULE
        );
        this.batchId = batchId;
        this.requestedQuantity = requestedQuantity;
        this.availableQuantity = availableQuantity;
    }
}
