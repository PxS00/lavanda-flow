package com.ceudelavanda.lavandaflow.inventory.application.initialsnapshot;

/** Stable, source-specific validation codes for rejected snapshot rows. */
public enum InitialInventoryImportValidationCode {
    MALFORMED_ROW,
    BLANK_NAME,
    INVALID_REFERENCE,
    INVALID_QUANTITY,
    INVALID_EXPIRATION,
    EXPIRATION_REQUIRED,
    EXPIRATION_BEFORE_EFFECTIVE_DATE,
    DUPLICATE_NAME
}
