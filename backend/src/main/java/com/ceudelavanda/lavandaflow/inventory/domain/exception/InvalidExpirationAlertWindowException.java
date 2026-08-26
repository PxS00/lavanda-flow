package com.ceudelavanda.lavandaflow.inventory.domain.exception;

import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;

/**
 * Raised when an expiration-alert window is negative.
 */
public final class InvalidExpirationAlertWindowException extends DomainException {

    public InvalidExpirationAlertWindowException(int windowDays) {
        super(
            "INVALID_EXPIRATION_ALERT_WINDOW",
            "Expiration alert window must be zero or positive: " + windowDays,
            ErrorType.VALIDATION
        );
    }
}
