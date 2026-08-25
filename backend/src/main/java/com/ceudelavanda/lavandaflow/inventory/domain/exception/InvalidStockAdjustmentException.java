package com.ceudelavanda.lavandaflow.inventory.domain.exception;

import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;

/**
 * Raised when a stock adjustment violates a mandatory adjustment rule.
 */
public final class InvalidStockAdjustmentException extends DomainException {

    public InvalidStockAdjustmentException() {
        this("Stock adjustment must not be zero");
    }

    public InvalidStockAdjustmentException(String message) {
        super(
            "INVALID_STOCK_ADJUSTMENT",
            message,
            ErrorType.VALIDATION
        );
    }
}
