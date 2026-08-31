package com.ceudelavanda.lavandaflow.inventory.domain.exception;

import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;

import java.util.Map;
import java.util.UUID;

/** Raised when a stock receipt references an inactive supplier. */
public final class InactiveSupplierException extends DomainException {

    public InactiveSupplierException(UUID supplierId) {
        super(
            "INACTIVE_SUPPLIER",
            "Supplier is inactive",
            ErrorType.BUSINESS_RULE,
            Map.of("supplierId", supplierId.toString())
        );
    }
}
