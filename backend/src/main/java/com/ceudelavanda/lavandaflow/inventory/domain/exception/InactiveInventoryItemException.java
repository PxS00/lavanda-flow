package com.ceudelavanda.lavandaflow.inventory.domain.exception;

import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;
import lombok.Getter;

import java.util.UUID;

/**
 * Raised when automatic stock withdrawal is requested for an inactive item.
 */
@Getter
public final class InactiveInventoryItemException extends DomainException {

    private final UUID inventoryItemId;

    public InactiveInventoryItemException(UUID inventoryItemId) {
        super(
            "INACTIVE_INVENTORY_ITEM",
            "Inventory item is inactive: " + inventoryItemId,
            ErrorType.BUSINESS_RULE
        );
        this.inventoryItemId = inventoryItemId;
    }
}
