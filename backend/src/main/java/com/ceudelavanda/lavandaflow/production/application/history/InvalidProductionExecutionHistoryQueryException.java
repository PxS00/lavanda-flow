package com.ceudelavanda.lavandaflow.production.application.history;

import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;

import java.util.Map;

/** Raised when production-history date filters or pagination are inconsistent. */
public final class InvalidProductionExecutionHistoryQueryException extends DomainException {
    public InvalidProductionExecutionHistoryQueryException(String field, String message) {
        super(
            "INVALID_PRODUCTION_EXECUTION_HISTORY_QUERY",
            "Production execution history query is invalid",
            ErrorType.VALIDATION,
            Map.of(field, message)
        );
    }
}
