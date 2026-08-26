package com.ceudelavanda.lavandaflow.inventory.infrastructure.web.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RegisterFefoWithdrawalRequest(
    @NotNull
    @Positive
    BigDecimal quantity,

    @Size(max = 255)
    String reason
) {
}
