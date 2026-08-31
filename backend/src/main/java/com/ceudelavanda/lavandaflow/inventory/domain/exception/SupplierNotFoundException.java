package com.ceudelavanda.lavandaflow.inventory.domain.exception;

import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;

import java.util.Map;
import java.util.UUID;

/** Raised when an inventory operation references a supplier that does not exist. */
public final class SupplierNotFoundException extends DomainException {

    public SupplierNotFoundException(UUID supplierId) {
        super(
            "SUPPLIER_NOT_FOUND",
            "Supplier not found",
            ErrorType.NOT_FOUND,
            Map.of("supplierId", supplierId.toString())
        );
    }
}
