package com.ceudelavanda.lavandaflow.inventory.application.initialsnapshot;

import java.time.LocalDate;
import java.util.List;

/** Deterministic result of validating or applying one initial inventory snapshot. */
public record InitialInventoryImportReport(
    InitialInventoryImportMode mode,
    LocalDate effectiveDate,
    int totalRowCount,
    int catalogOnlyCount,
    int openingStockCount,
    int rejectedCount,
    List<InitialInventoryImportRowResult> rows
) {
    public InitialInventoryImportReport {
        rows = List.copyOf(rows);
    }
}
