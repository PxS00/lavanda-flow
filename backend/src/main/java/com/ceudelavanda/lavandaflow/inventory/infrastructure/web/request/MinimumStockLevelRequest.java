package com.ceudelavanda.lavandaflow.inventory.infrastructure.web.request;

import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public record MinimumStockLevelRequest(
    @NotNull
    @Schema(description = "Positive minimum stock quantity with at most six meaningful decimal places.", example = "250.000000")
    BigDecimal minimumQuantity
) {
}
