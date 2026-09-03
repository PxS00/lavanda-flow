package com.ceudelavanda.lavandaflow.production.domain.exception;

import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;

import java.util.Map;

/** Raised when a production formula violates a structural or quantity invariant. */
public final class InvalidProductionFormulaException extends DomainException {

    public InvalidProductionFormulaException(String field, String message) {
        super(
            "INVALID_PRODUCTION_FORMULA",
            message,
            ErrorType.VALIDATION,
            Map.of(field, message)
        );
    }
}
