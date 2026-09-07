package com.ceudelavanda.lavandaflow.production.infrastructure.web.request;

import com.ceudelavanda.lavandaflow.production.domain.ProductionLotCodeMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RegisterProductionRequest(
    @NotNull
    @Schema(description = "Production formula identifier")
    UUID formulaId,
    @NotNull
    @Positive
    @Digits(integer = 13, fraction = 6)
    @Schema(description = "Positive quantity produced in the formula output item's unit")
    BigDecimal outputQuantity,
    @NotEmpty
    @Schema(description = "Exact concrete source-batch quantities actually consumed")
    List<@NotNull @Valid ProductionSourceAllocationRequest> sourceAllocations,
    @NotNull
    @Schema(description = "Authoritative production date used by generated internal lot allocation")
    LocalDate productionDate,
    @NotNull
    @Schema(description = "Date recorded on the internally produced output batch")
    LocalDate outputReceivedAt,
    @Schema(description = "Optional explicit expiration date for the output batch")
    LocalDate outputExpiresAt,
    @NotNull
    @Schema(description = "Definitive lot-code strategy", allowableValues = {"GENERATED", "MANUAL"})
    ProductionLotCodeMode lotCodeMode,
    @Size(max = 255)
    @Schema(description = "Explicit internal lot code when lotCodeMode is MANUAL")
    String manualLotCode
) {
}
