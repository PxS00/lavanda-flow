package com.ceudelavanda.lavandaflow.catalog.application;

import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;

import java.util.Map;

/** Raised when inventory-item search filters or pagination are invalid. */
public final class InvalidInventoryItemSearchQueryException extends DomainException {

    public InvalidInventoryItemSearchQueryException(String field, String message) {
        super(
            "INVALID_INVENTORY_ITEM_SEARCH_QUERY",
            "Inventory item search query is invalid",
            ErrorType.VALIDATION,
            Map.of(field, message)
        );
    }
}
