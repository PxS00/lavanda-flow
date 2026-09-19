package com.ceudelavanda.lavandaflow.production.application.history;

import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;

import java.util.Map;
import java.util.UUID;

/** Raised when a completed production execution identity does not exist. */
public final class ProductionExecutionNotFoundException extends DomainException {
    public ProductionExecutionNotFoundException(UUID executionId) {
        super(
            "PRODUCTION_EXECUTION_NOT_FOUND",
            "Production execution was not found",
            ErrorType.NOT_FOUND,
            Map.of("executionId", String.valueOf(executionId))
        );
    }
}
