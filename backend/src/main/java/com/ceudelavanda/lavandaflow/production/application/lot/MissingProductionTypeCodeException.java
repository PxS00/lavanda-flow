package com.ceudelavanda.lavandaflow.production.application.lot;

import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;

import java.util.Map;
import java.util.UUID;

/** Raised when an output item has no stable catalog production type code. */
public final class MissingProductionTypeCodeException extends DomainException {

    public MissingProductionTypeCodeException(UUID inventoryItemId) {
        super(
            "MISSING_PRODUCTION_TYPE_CODE",
            "Output inventory item requires a production type code for internal lot allocation",
            ErrorType.BUSINESS_RULE,
            Map.of("inventoryItemId", inventoryItemId.toString())
        );
    }
}
