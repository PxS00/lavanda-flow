package com.ceudelavanda.lavandaflow.inventory.domain.exception;

import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;

/** Raised when an operation requiring an audit reason receives an invalid one. */
public final class InvalidStockMovementReasonException extends DomainException {

    public InvalidStockMovementReasonException() {
        super(
            "INVALID_STOCK_MOVEMENT_REASON",
            "Stock movement reason must not be blank or exceed 255 characters",
            ErrorType.VALIDATION
        );
    }
}
