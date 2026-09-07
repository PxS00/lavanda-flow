package com.ceudelavanda.lavandaflow.production.application.execution;

import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;

import java.util.Map;
import java.util.UUID;

public final class InactiveProductionRegistrationCatalogItemException extends DomainException {

    public InactiveProductionRegistrationCatalogItemException(UUID inventoryItemId) {
        super(
            "INACTIVE_PRODUCTION_REGISTRATION_CATALOG_ITEM",
            "Inactive catalog item cannot participate in production registration",
            ErrorType.BUSINESS_RULE,
            Map.of("inventoryItemId", String.valueOf(inventoryItemId))
        );
    }
}
