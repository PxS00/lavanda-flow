package com.ceudelavanda.lavandaflow.inventory.application.result;

import java.time.LocalDate;
import java.util.List;

/**
 * Immutable expiration-alert view evaluated for one application date and window.
 */
public record ExpirationAlertsResult(
    LocalDate asOfDate,
    int windowDays,
    List<ExpirationAlertEntryResult> alerts
) {
    public ExpirationAlertsResult {
        alerts = List.copyOf(alerts);
    }
}
