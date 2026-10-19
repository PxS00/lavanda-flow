package com.ceudelavanda.lavandaflow.customers.application;

import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;
import java.util.Map;

/** Normalized contact values failed standard Bean Validation. */
public final class InvalidCustomerContactException extends DomainException {
    public InvalidCustomerContactException(Map<String, String> details) {
        super("VALIDATION_ERROR", "Request validation failed", ErrorType.VALIDATION, details);
    }
}
