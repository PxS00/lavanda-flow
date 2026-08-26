package com.ceudelavanda.lavandaflow.inventory.application.result;

import java.time.LocalDate;
import java.util.List;

/**
 * Immutable expiration-alert view evaluated for one application date and future alert window.
 *
 * @param asOfDate application date used for all expiration calculations
 * @param windowDays inclusive future alert horizon in days
 * @param alerts deterministically ordered expired and expiring-soon batch alerts
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
