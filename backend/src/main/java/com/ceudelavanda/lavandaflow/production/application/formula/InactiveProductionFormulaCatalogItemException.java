package com.ceudelavanda.lavandaflow.production.application.formula;

import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;

import java.util.Map;
import java.util.UUID;

public final class InactiveProductionFormulaCatalogItemException extends DomainException {

    public InactiveProductionFormulaCatalogItemException(UUID inventoryItemId) {
        super(
            "INACTIVE_PRODUCTION_FORMULA_CATALOG_ITEM",
            "Inactive inventory items cannot be used in a production formula",
            ErrorType.BUSINESS_RULE,
            Map.of("inventoryItemId", inventoryItemId.toString())
        );
    }
}
