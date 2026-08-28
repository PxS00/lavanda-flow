package com.ceudelavanda.lavandaflow.inventory.application.alerts;

import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;

import java.math.BigDecimal;
import java.util.UUID;

public record LowStockAlertEntryResult(
    UUID inventoryItemId,
    String name,
    UnitOfMeasure unitOfMeasure,
    BigDecimal availableQuantity,
    BigDecimal minimumQuantity,
    BigDecimal deficitQuantity
) {
}
