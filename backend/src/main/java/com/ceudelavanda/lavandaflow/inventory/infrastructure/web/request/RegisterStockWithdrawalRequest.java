package com.ceudelavanda.lavandaflow.inventory.infrastructure.web.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RegisterStockWithdrawalRequest(

    @NotNull
    @Positive
    @Digits(integer = 13, fraction = 6)
    BigDecimal quantity,

    @Size(max = 255)
    String reason
) {
}
