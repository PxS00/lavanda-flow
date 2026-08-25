package com.ceudelavanda.lavandaflow.inventory.domain.exception;

import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.UUID;

@Getter
public final class BatchNotFoundException extends DomainException {

    private final UUID batchId;

    public BatchNotFoundException(UUID batchId) {
        super(
            "BATCH_NOT_FOUND",
            "Batch not found: " + batchId,
            HttpStatus.NOT_FOUND
        );
        this.batchId = batchId;
    }
}
