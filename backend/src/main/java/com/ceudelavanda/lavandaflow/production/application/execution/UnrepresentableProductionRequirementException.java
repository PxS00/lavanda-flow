package com.ceudelavanda.lavandaflow.production.application.execution;

import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;

import java.util.Map;
import java.util.UUID;

/** Raised when linear formula scaling cannot be represented exactly at inventory precision. */
public final class UnrepresentableProductionRequirementException extends DomainException {

    public UnrepresentableProductionRequirementException(UUID inventoryItemId) {
        super(
            "UNREPRESENTABLE_PRODUCTION_REQUIREMENT",
            "Scaled formula requirement cannot be represented exactly with six fractional digits",
            ErrorType.BUSINESS_RULE,
            Map.of("inventoryItemId", String.valueOf(inventoryItemId))
        );
    }
}
