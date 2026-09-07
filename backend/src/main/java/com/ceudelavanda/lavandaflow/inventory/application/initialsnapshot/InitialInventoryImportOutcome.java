package com.ceudelavanda.lavandaflow.inventory.application.initialsnapshot;

/** Deterministic business outcome for one source row. */
public enum InitialInventoryImportOutcome {
    CATALOG_ONLY,
    OPENING_STOCK,
    REJECTED
}
