package com.ceudelavanda.lavandaflow.inventory.infrastructure.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configurable defaults used by inventory alert queries.
 */
@Validated
@ConfigurationProperties(prefix = "lavanda.inventory.alerts")
public record InventoryAlertProperties(
    @Min(value = 0, message = "Expiration alert window must be zero or positive")
    int expirationWindowDays
) {
}
