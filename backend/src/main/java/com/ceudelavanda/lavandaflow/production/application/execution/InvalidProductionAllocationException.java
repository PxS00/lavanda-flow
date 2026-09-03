package com.ceudelavanda.lavandaflow.production.application.execution;

import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;

import java.util.Map;
import java.util.UUID;

/** Raised when exact source-batch totals do not match the scaled formula requirements. */
public final class InvalidProductionAllocationException extends DomainException {

    public InvalidProductionAllocationException(UUID inventoryItemId, String expected, String actual) {
        super(
            "INVALID_PRODUCTION_ALLOCATION",
            "Exact source allocations do not match the scaled production formula",
            ErrorType.BUSINESS_RULE,
            Map.of(
                "inventoryItemId", String.valueOf(inventoryItemId),
                "expectedQuantity", expected,
                "actualQuantity", actual
            )
        );
    }
}
