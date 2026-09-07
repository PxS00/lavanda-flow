package com.ceudelavanda.lavandaflow.production.application.genealogy;

import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;

import java.util.UUID;

/** Preserves the inventory API's stable missing-batch error contract at the genealogy boundary. */
public final class BatchGenealogyNotFoundException extends DomainException {

    public BatchGenealogyNotFoundException(UUID batchId) {
        super(
            "BATCH_NOT_FOUND",
            "Batch not found: " + batchId,
            ErrorType.NOT_FOUND
        );
    }
}
