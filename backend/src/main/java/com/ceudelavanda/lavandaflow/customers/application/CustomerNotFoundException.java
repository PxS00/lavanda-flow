package com.ceudelavanda.lavandaflow.customers.application;

import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;
import java.util.Map;
import java.util.UUID;

/** Standard customer not_found error. */
public final class CustomerNotFoundException extends DomainException {
    public CustomerNotFoundException(UUID customerId) {
        super("CUSTOMER_NOT_FOUND", "Customer not found", ErrorType.NOT_FOUND, Map.of("customerId", customerId.toString()));
    }
}
