package com.ceudelavanda.lavandaflow.inventory.infrastructure.web.response;

import com.ceudelavanda.lavandaflow.inventory.application.result.FefoWithdrawalAllocationResult;

import java.math.BigDecimal;
import java.util.UUID;

public record FefoWithdrawalAllocationResponse(
    UUID batchId,
    UUID movementId,
    BigDecimal quantity
) {

    static FefoWithdrawalAllocationResponse from(FefoWithdrawalAllocationResult result) {
        return new FefoWithdrawalAllocationResponse(
            result.batchId(),
            result.movementId(),
            result.quantity()
        );
    }
}
