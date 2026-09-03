package com.ceudelavanda.lavandaflow.production.infrastructure.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductionFormulaIngredientRequest(
    @NotNull
    @Schema(description = "Stable catalog inventory item identifier")
    UUID inventoryItemId,
    @NotNull
    @Positive
    @Digits(integer = 13, fraction = 6)
    @Schema(description = "Positive requirement quantity in the catalog item's unit of measure")
    BigDecimal quantity
) {
}
