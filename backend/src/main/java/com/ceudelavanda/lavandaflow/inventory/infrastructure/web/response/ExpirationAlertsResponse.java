package com.ceudelavanda.lavandaflow.inventory.infrastructure.web.response;

import com.ceudelavanda.lavandaflow.inventory.application.alerts.ExpirationAlertsResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

public record ExpirationAlertsResponse(
    @Schema(description = "Application date used to evaluate all expiration states using the configured application Clock.", example = "2026-08-26") LocalDate asOfDate,
    @Schema(description = "Resolved non-negative future alert window in days. This is the request override when supplied, otherwise the configured default.", minimum = "0", example = "30") int windowDays,
    @Schema(description = "Expired and expiring-soon positive-balance batches, ordered by expiration date, inventory item ID, and batch ID.") List<ExpirationAlertEntryResponse> alerts
) {
    public ExpirationAlertsResponse {
        alerts = List.copyOf(alerts);
    }

    public static ExpirationAlertsResponse from(ExpirationAlertsResult result) {
        return new ExpirationAlertsResponse(
            result.asOfDate(), result.windowDays(),
            result.alerts().stream().map(ExpirationAlertEntryResponse::from).toList()
        );
    }
}
