package com.ceudelavanda.lavandaflow.inventory.infrastructure.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RegisterStockAdjustmentRequest(

    @NotNull
    BigDecimal quantity,

    @NotBlank
    @Size(max = 255)
    String reason
) {
}
