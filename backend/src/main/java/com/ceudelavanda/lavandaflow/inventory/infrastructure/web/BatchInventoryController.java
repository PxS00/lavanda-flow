package com.ceudelavanda.lavandaflow.inventory.infrastructure.web;

import com.ceudelavanda.lavandaflow.inventory.application.batch.GetBatchInventory;
import com.ceudelavanda.lavandaflow.inventory.infrastructure.web.response.BatchInventoryResponse;
import com.ceudelavanda.lavandaflow.shared.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/inventory")
public class BatchInventoryController {

    private final GetBatchInventory getBatchInventory;

    @Operation(
        summary = "Retrieve inventory batches for an item",
        description = "Returns all persisted batches for an existing inventory item. Results are ordered by expiration date ascending with batches without expiration last, then by received date and batch ID. Status is derived at read time: ZERO_BALANCE takes precedence; positive-balance batches expiring on or before asOfDate are EXPIRED; all remaining batches are AVAILABLE."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Batch inventory retrieved successfully", content = @Content(schema = @Schema(implementation = BatchInventoryResponse.class))),
        @ApiResponse(responseCode = "404", description = "Inventory item not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/items/{inventoryItemId}/batches")
    public ResponseEntity<BatchInventoryResponse> getBatchInventory(@PathVariable UUID inventoryItemId) {
        return ResponseEntity.ok(BatchInventoryResponse.from(getBatchInventory.execute(inventoryItemId)));
    }
}
