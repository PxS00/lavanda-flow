package com.ceudelavanda.lavandaflow.production.infrastructure.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record UpsertProductionFormulaRequest(
    @NotNull
    @Schema(description = "Stable catalog inventory item identifier produced by the formula")
    UUID outputInventoryItemId,
    @NotNull
    @Positive
    @Digits(integer = 13, fraction = 6)
    @Schema(description = "Positive reference output quantity in the catalog item's unit of measure")
    BigDecimal outputQuantity,
    @NotEmpty
    @Valid
    @Schema(description = "One or more catalog item requirements; duplicate item identifiers are rejected")
    List<@NotNull @Valid ProductionFormulaIngredientRequest> ingredients
) {
}
