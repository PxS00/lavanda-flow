package com.ceudelavanda.lavandaflow.customers.application;

import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;
import java.util.Map;

/** Standard customer validation error. */
public final class InvalidCustomerSearchQueryException extends DomainException {
    public InvalidCustomerSearchQueryException(String field, String message) {
        super("INVALID_CUSTOMER_SEARCH_QUERY", "Customer search query is invalid", ErrorType.VALIDATION, Map.of(field, message));
    }
}
