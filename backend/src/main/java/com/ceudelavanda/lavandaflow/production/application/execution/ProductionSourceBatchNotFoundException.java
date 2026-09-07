package com.ceudelavanda.lavandaflow.production.application.execution;

import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;

import java.util.Map;
import java.util.UUID;

public final class ProductionSourceBatchNotFoundException extends DomainException {

    public ProductionSourceBatchNotFoundException(UUID batchId) {
        super(
            "PRODUCTION_SOURCE_BATCH_NOT_FOUND",
            "Production source batch was not found",
            ErrorType.NOT_FOUND,
            Map.of("batchId", String.valueOf(batchId))
        );
    }
}
