package com.ceudelavanda.lavandaflow.inventory.infrastructure.web.response;

import com.ceudelavanda.lavandaflow.inventory.application.batch.BatchInventoryResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record BatchInventoryResponse(
    UUID inventoryItemId,
    @Schema(description = "Application business date used to derive batch operational status.", example = "2026-08-28")
    LocalDate asOfDate,
    @Schema(description = "Batches ordered by expiration date with no-expiration batches last, then received date and batch ID.")
    List<BatchInventoryEntryResponse> batches
) {
    public BatchInventoryResponse {
        batches = List.copyOf(batches);
    }

    public static BatchInventoryResponse from(BatchInventoryResult result) {
        return new BatchInventoryResponse(
            result.inventoryItemId(),
            result.asOfDate(),
            result.batches().stream().map(BatchInventoryEntryResponse::from).toList()
        );
    }
}
