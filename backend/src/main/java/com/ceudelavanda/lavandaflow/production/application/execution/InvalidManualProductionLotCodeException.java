package com.ceudelavanda.lavandaflow.production.application.execution;

import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;

/** Raised when manual/generated lot inputs are missing or ambiguous. */
public final class InvalidManualProductionLotCodeException extends DomainException {

    public InvalidManualProductionLotCodeException(String message) {
        super(
            "INVALID_MANUAL_PRODUCTION_LOT_CODE",
            message,
            ErrorType.VALIDATION
        );
    }
}
