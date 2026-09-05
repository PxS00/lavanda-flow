package com.ceudelavanda.lavandaflow.inventory.infrastructure.web.response;

import com.ceudelavanda.lavandaflow.inventory.application.dashboard.OperationalDashboardSummary;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Backend-authoritative operational inventory dashboard summary")
public record InventoryDashboardResponse(
    @Schema(description = "Business date resolved from the application clock for every counter")
    LocalDate asOfDate,
    @Schema(description = "Configured non-negative expiration window in days; its future cutoff is inclusive")
    int expirationWindowDays,
    @Schema(description = "Number of active catalog inventory items")
    long activeItemCount,
    @Schema(description = "Number of active items whose available stock is below their configured minimum")
    long lowStockItemCount,
    @Schema(description = "Number of active items with zero available stock; expired and expiring-today stock is unavailable")
    long outOfStockItemCount,
    @Schema(description = "Number of positive-balance batches expiring after asOfDate through the inclusive configured cutoff")
    long expiringSoonBatchCount,
    @Schema(description = "Number of positive-balance batches expiring on or before asOfDate")
    long expiredBatchCount
) {

    public static InventoryDashboardResponse from(OperationalDashboardSummary result) {
        return new InventoryDashboardResponse(
            result.asOfDate(),
            result.expirationWindowDays(),
            result.activeItemCount(),
            result.lowStockItemCount(),
            result.outOfStockItemCount(),
            result.expiringSoonBatchCount(),
            result.expiredBatchCount()
        );
    }
}
