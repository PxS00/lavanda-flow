package com.ceudelavanda.lavandaflow.production.application.lot;

import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;

import java.util.Map;
import java.util.UUID;

/** Raised when the output item cannot provide the catalog metadata required for allocation. */
public final class ProductionLotCodeCatalogItemNotFoundException extends DomainException {

    public ProductionLotCodeCatalogItemNotFoundException(UUID inventoryItemId) {
        super(
            "PRODUCTION_LOT_CODE_CATALOG_ITEM_NOT_FOUND",
            "Output inventory item required for internal production lot allocation was not found",
            ErrorType.NOT_FOUND,
            Map.of("inventoryItemId", inventoryItemId.toString())
        );
    }
}
