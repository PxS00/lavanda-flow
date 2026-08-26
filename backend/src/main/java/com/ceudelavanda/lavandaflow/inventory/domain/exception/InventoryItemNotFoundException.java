package com.ceudelavanda.lavandaflow.inventory.domain.exception;

import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;
import lombok.Getter;

import java.util.UUID;

/**
 * Raised when an inventory operation targets an inventory item that does not exist.
 */
@Getter
public final class InventoryItemNotFoundException extends DomainException {

    private final UUID inventoryItemId;

    public InventoryItemNotFoundException(UUID inventoryItemId) {
        super(
            "INVENTORY_ITEM_NOT_FOUND",
            "Inventory item not found: " + inventoryItemId,
            ErrorType.NOT_FOUND
        );
        this.inventoryItemId = inventoryItemId;
    }
}
