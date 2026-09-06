package com.ceudelavanda.lavandaflow.inventory.application.initialsnapshot;

/** Apply failure that retains the deterministic validation report for operator output. */
public class InitialInventoryImportException extends IllegalStateException {

    private final InitialInventoryImportReport report;

    public InitialInventoryImportException(String message, InitialInventoryImportReport report) {
        super(message);
        this.report = report;
    }

    public InitialInventoryImportReport report() {
        return report;
    }
}
