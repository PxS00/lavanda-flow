package com.ceudelavanda.lavandaflow.inventory.domain.exception;

import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;

import java.util.UUID;

public final class MinimumStockLevelNotFoundException extends DomainException {

    public MinimumStockLevelNotFoundException(UUID inventoryItemId) {
        super(
            "MINIMUM_STOCK_LEVEL_NOT_FOUND",
            "Minimum stock level not found for inventory item: " + inventoryItemId,
            ErrorType.NOT_FOUND
        );
    }
}
