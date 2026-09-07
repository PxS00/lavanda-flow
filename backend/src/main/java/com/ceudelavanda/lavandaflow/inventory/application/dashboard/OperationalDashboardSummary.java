package com.ceudelavanda.lavandaflow.inventory.application.dashboard;

import java.time.LocalDate;

/** Backend-authoritative operational counters for the inventory dashboard. */
public record OperationalDashboardSummary(
    LocalDate asOfDate,
    int expirationWindowDays,
    long activeItemCount,
    long lowStockItemCount,
    long outOfStockItemCount,
    long expiringSoonBatchCount,
    long expiredBatchCount
) {
}
