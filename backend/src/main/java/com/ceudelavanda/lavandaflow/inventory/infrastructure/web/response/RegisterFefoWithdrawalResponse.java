package com.ceudelavanda.lavandaflow.inventory.infrastructure.web.response;

import com.ceudelavanda.lavandaflow.inventory.application.result.FefoWithdrawalResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record RegisterFefoWithdrawalResponse(
    UUID inventoryItemId,
    BigDecimal requestedQuantity,
    BigDecimal allocatedQuantity,
    List<FefoWithdrawalAllocationResponse> allocations
) {

    public static RegisterFefoWithdrawalResponse from(FefoWithdrawalResult result) {
        return new RegisterFefoWithdrawalResponse(
            result.inventoryItemId(),
            result.requestedQuantity(),
            result.allocatedQuantity(),
            result.allocations().stream()
                .map(FefoWithdrawalAllocationResponse::from)
                .toList()
        );
    }
}
