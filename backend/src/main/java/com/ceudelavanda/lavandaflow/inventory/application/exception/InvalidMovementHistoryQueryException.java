package com.ceudelavanda.lavandaflow.inventory.application.exception;

import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;

import java.util.Map;

/**
 * Raised when movement-history filters or pagination parameters are inconsistent.
 */
public final class InvalidMovementHistoryQueryException extends DomainException {

    public InvalidMovementHistoryQueryException(String field, String message) {
        super(
            "INVALID_MOVEMENT_HISTORY_QUERY",
            "Movement history query is invalid",
            ErrorType.VALIDATION,
            Map.of(field, message)
        );
    }
}
