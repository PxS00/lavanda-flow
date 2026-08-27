package com.ceudelavanda.lavandaflow.inventory.domain.exception;

import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;

import java.math.BigDecimal;

public final class InvalidMinimumStockQuantityException extends DomainException {

    public InvalidMinimumStockQuantityException(BigDecimal quantity) {
        super(
            "INVALID_MINIMUM_STOCK_QUANTITY",
            "Minimum stock quantity must be positive and have at most six decimal places: " + quantity,
            ErrorType.VALIDATION
        );
    }
}
