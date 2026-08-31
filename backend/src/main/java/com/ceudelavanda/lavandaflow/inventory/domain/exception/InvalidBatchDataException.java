package com.ceudelavanda.lavandaflow.inventory.domain.exception;

import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;

import java.util.Map;

/** Raised when structural batch data violates an inventory invariant. */
public final class InvalidBatchDataException extends DomainException {

    public InvalidBatchDataException(String field, String message) {
        super(
            "INVALID_BATCH_DATA",
            message,
            ErrorType.VALIDATION,
            Map.of(field, message)
        );
    }
}
