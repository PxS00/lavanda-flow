package com.ceudelavanda.lavandaflow.inventory.application.initialsnapshot;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Immutable normalized report entry for one data row in source order. */
public record InitialInventoryImportRowResult(
    int sourceRowNumber,
    String catalogName,
    String gender,
    String essenceReference,
    String productionTypeCode,
    String lotCode,
    BigDecimal quantity,
    LocalDate expiration,
    InitialInventoryImportOutcome outcome,
    InitialInventoryImportValidationCode validationCode,
    String validationReason
) {
}
