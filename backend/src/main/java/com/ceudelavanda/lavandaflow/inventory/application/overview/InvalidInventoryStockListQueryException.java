package com.ceudelavanda.lavandaflow.inventory.application.overview;

import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;

import java.util.Map;

public final class InvalidInventoryStockListQueryException extends DomainException {

    public InvalidInventoryStockListQueryException(String field, String message) {
        super(
            "INVALID_INVENTORY_STOCK_LIST_QUERY",
            "Inventory stock list query is invalid",
            ErrorType.VALIDATION,
            Map.of(field, message)
        );
    }
}
