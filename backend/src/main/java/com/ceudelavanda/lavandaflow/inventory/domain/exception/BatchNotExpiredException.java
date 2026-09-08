package com.ceudelavanda.lavandaflow.inventory.domain.exception;

import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;

import java.util.Map;
import java.util.UUID;

/** Raised when a batch is not eligible for expired-stock disposal. */
public final class BatchNotExpiredException extends DomainException {

    public BatchNotExpiredException(UUID batchId) {
        super(
            "BATCH_NOT_EXPIRED",
            "Batch is not expired: " + batchId,
            ErrorType.BUSINESS_RULE,
            Map.of("batchId", batchId.toString())
        );
    }
}
