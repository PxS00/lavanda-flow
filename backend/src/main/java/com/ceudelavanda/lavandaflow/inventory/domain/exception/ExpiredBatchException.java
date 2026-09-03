package com.ceudelavanda.lavandaflow.inventory.domain.exception;

import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;

import java.util.Map;
import java.util.UUID;

/** Raised when an exact-source operation attempts to consume an expired batch. */
public final class ExpiredBatchException extends DomainException {

    public ExpiredBatchException(UUID batchId) {
        super(
            "EXPIRED_BATCH",
            "Expired batch cannot be consumed: " + batchId,
            ErrorType.BUSINESS_RULE,
            Map.of("batchId", batchId.toString())
        );
    }
}
