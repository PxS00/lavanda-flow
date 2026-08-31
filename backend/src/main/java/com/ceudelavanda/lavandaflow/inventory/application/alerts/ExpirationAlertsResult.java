package com.ceudelavanda.lavandaflow.inventory.application.alerts;

import java.time.LocalDate;
import java.util.List;

public record ExpirationAlertsResult(LocalDate asOfDate, int windowDays, List<ExpirationAlertEntryResult> alerts) {
    public ExpirationAlertsResult {
        alerts = List.copyOf(alerts);
    }
}
