package com.ceudelavanda.lavandaflow.suppliers.application;

import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;

import java.util.Map;
import java.util.UUID;

/** Raised when a supplier cannot be found. */
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
