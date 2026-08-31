package com.ceudelavanda.lavandaflow.inventory.application.overview;

import com.ceudelavanda.lavandaflow.inventory.domain.exception.InvalidExpirationAlertWindowException;

import java.util.UUID;

/** Input for retrieving one inventory item's operational overview. */
public record GetInventoryItemOverviewQuery(UUID inventoryItemId, int expirationWindowDays) {

    public GetInventoryItemOverviewQuery {
        if (inventoryItemId == null) {
            throw new IllegalArgumentException("inventoryItemId must not be null");
        }
        if (expirationWindowDays < 0) {
            throw new InvalidExpirationAlertWindowException(expirationWindowDays);
        }
    }
}
