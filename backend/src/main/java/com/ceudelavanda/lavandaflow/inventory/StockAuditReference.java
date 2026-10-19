package com.ceudelavanda.lavandaflow.inventory;

import java.util.Objects;
import java.util.UUID;

/** Opaque source identity. Inventory has no dependency on the source module. */
public record StockAuditReference(String referenceType, UUID referenceId, UUID referenceLineId) {
    public StockAuditReference {
        if (referenceType == null || referenceType.isBlank() || referenceType.length() > 32)
            throw new IllegalArgumentException("referenceType must contain 1–32 characters");
        Objects.requireNonNull(referenceId, "referenceId");
        Objects.requireNonNull(referenceLineId, "referenceLineId");
    }
}
