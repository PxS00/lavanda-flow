package com.ceudelavanda.lavandaflow.inventory.infrastructure.web.response;

import com.ceudelavanda.lavandaflow.inventory.application.result.LowStockAlertsResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

public record LowStockAlertsResponse(
    @Schema(description = "Business date used to determine available stock.", example = "2026-08-26")
    LocalDate asOfDate,
    List<LowStockAlertEntryResponse> alerts
) {
    public LowStockAlertsResponse {
        alerts = List.copyOf(alerts);
    }

    public static LowStockAlertsResponse from(LowStockAlertsResult result) {
        return new LowStockAlertsResponse(
            result.asOfDate(),
            result.alerts().stream().map(LowStockAlertEntryResponse::from).toList()
        );
    }
}
