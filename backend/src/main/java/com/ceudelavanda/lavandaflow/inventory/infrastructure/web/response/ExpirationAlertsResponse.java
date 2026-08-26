package com.ceudelavanda.lavandaflow.inventory.infrastructure.web.response;

import com.ceudelavanda.lavandaflow.inventory.application.result.ExpirationAlertsResult;

import java.time.LocalDate;
import java.util.List;

public record ExpirationAlertsResponse(
    LocalDate asOfDate,
    int windowDays,
    List<ExpirationAlertEntryResponse> alerts
) {
    public ExpirationAlertsResponse {
        alerts = List.copyOf(alerts);
    }

    public static ExpirationAlertsResponse from(ExpirationAlertsResult result) {
        return new ExpirationAlertsResponse(
            result.asOfDate(),
            result.windowDays(),
            result.alerts().stream()
                .map(ExpirationAlertEntryResponse::from)
                .toList()
        );
    }
}
