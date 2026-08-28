package com.ceudelavanda.lavandaflow.suppliers.application;

import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;

import java.util.Map;

/** Raised when supplier search filters or pagination are invalid. */
public final class InvalidSupplierSearchQueryException extends DomainException {

    public InvalidSupplierSearchQueryException(String field, String message) {
        super(
            "INVALID_SUPPLIER_SEARCH_QUERY",
            "Supplier search query is invalid",
            ErrorType.VALIDATION,
            Map.of(field, message)
        );
    }
}
