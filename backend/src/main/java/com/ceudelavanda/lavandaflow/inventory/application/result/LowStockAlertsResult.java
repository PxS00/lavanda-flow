package com.ceudelavanda.lavandaflow.inventory.application.result;

import java.time.LocalDate;
import java.util.List;

public record LowStockAlertsResult(LocalDate asOfDate, List<LowStockAlertEntryResult> alerts) {
    public LowStockAlertsResult {
        alerts = List.copyOf(alerts);
    }
}
