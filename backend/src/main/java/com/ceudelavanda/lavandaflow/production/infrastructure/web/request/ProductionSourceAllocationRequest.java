package com.ceudelavanda.lavandaflow.production.infrastructure.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductionSourceAllocationRequest(
    @NotNull
    @Schema(description = "Concrete inventory batch identifier actually consumed")
    UUID batchId,
    @NotNull
    @Positive
    @Digits(integer = 13, fraction = 6)
    @Schema(description = "Exact positive quantity consumed from this batch")
    BigDecimal quantity
) {
}
