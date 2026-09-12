package com.ceudelavanda.lavandaflow.catalog.domain;

import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;

import java.util.Map;

/** Rejects fragrance gender metadata on a category that cannot carry it. */
public final class InvalidProductGenderException extends DomainException {
    public InvalidProductGenderException() {
        super("VALIDATION_ERROR", "Invalid catalog gender", ErrorType.VALIDATION,
            Map.of("gender", "gender is only valid for ESSENCE or FINISHED_PRODUCT items"));
    }
}
