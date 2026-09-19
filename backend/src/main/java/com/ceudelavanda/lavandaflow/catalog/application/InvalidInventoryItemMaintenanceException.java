package com.ceudelavanda.lavandaflow.catalog.application;

import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;

import java.util.Map;

/** Exposes safe, structured failures from the restricted catalog update. */
public final class InvalidInventoryItemMaintenanceException extends DomainException {

    private InvalidInventoryItemMaintenanceException(
        String code,
        String message,
        ErrorType errorType,
        String field,
        String detail
    ) {
        super(code, message, errorType, Map.of(field, detail));
    }

    public static InvalidInventoryItemMaintenanceException immutable(String field) {
        return new InvalidInventoryItemMaintenanceException(
            "INVENTORY_ITEM_STABLE_METADATA_IMMUTABLE",
            "Assigned catalog metadata cannot be changed or removed",
            ErrorType.BUSINESS_RULE,
            field,
            "Assigned value can only be submitted unchanged"
        );
    }

    public static InvalidInventoryItemMaintenanceException invalid(String field) {
        return new InvalidInventoryItemMaintenanceException(
            "INVALID_INVENTORY_ITEM_METADATA",
            "Inventory item metadata is invalid",
            ErrorType.VALIDATION,
            field,
            "Value is invalid for this inventory item"
        );
    }

    public static InvalidInventoryItemMaintenanceException canonicalEssenceReferenceConflict() {
        return new InvalidInventoryItemMaintenanceException(
            "INVENTORY_ITEM_CANONICAL_ESSENCE_REFERENCE_CONFLICT",
            "Essence reference is already assigned to a canonical essence",
            ErrorType.CONFLICT,
            "essenceReference",
            "Reference is already assigned to another canonical essence"
        );
    }
}
