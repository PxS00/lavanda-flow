package com.ceudelavanda.lavandaflow.catalog.application;

import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;

import java.util.Map;
import java.util.UUID;

/** Raised when an inventory catalog item cannot be found. */
public final class InventoryItemNotFoundException extends DomainException {

    public InventoryItemNotFoundException(UUID inventoryItemId) {
        super(
            "INVENTORY_ITEM_NOT_FOUND",
            "Inventory item not found",
            ErrorType.NOT_FOUND,
            Map.of("inventoryItemId", inventoryItemId.toString())
        );
    }
}
