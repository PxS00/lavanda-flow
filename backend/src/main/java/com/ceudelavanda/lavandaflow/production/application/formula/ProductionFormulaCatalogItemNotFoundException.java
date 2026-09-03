package com.ceudelavanda.lavandaflow.production.application.formula;

import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;

import java.util.Map;
import java.util.UUID;

public final class ProductionFormulaCatalogItemNotFoundException extends DomainException {

    public ProductionFormulaCatalogItemNotFoundException(UUID inventoryItemId) {
        super(
            "PRODUCTION_FORMULA_CATALOG_ITEM_NOT_FOUND",
            "Inventory item referenced by production formula was not found",
            ErrorType.NOT_FOUND,
            Map.of("inventoryItemId", inventoryItemId.toString())
        );
    }
}
