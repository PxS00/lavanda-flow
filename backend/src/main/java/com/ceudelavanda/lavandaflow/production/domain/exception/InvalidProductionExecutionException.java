package com.ceudelavanda.lavandaflow.production.domain.exception;

import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;

import java.util.Map;

/** Raised when completed production history would violate production invariants. */
public final class InvalidProductionExecutionException extends DomainException {

    public InvalidProductionExecutionException(String field, String message) {
        super(
            "INVALID_PRODUCTION_EXECUTION",
            message,
            ErrorType.VALIDATION,
            Map.of("field", field)
        );
    }
}
