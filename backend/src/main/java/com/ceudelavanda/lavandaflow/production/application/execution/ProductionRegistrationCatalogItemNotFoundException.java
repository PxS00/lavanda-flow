package com.ceudelavanda.lavandaflow.production.application.execution;

import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;

import java.util.Map;
import java.util.UUID;

public final class ProductionRegistrationCatalogItemNotFoundException extends DomainException {

    public ProductionRegistrationCatalogItemNotFoundException(UUID inventoryItemId) {
        super(
            "PRODUCTION_REGISTRATION_CATALOG_ITEM_NOT_FOUND",
            "Catalog item referenced by the production formula was not found",
            ErrorType.NOT_FOUND,
            Map.of("inventoryItemId", String.valueOf(inventoryItemId))
        );
    }
}
