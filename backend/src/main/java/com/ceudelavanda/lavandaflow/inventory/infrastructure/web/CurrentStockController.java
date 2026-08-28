package com.ceudelavanda.lavandaflow.inventory.infrastructure.web;

import com.ceudelavanda.lavandaflow.inventory.application.stock.GetCurrentStock;
import com.ceudelavanda.lavandaflow.inventory.application.stock.GetCurrentStockQuery;
import com.ceudelavanda.lavandaflow.inventory.infrastructure.web.response.CurrentStockResponse;
import com.ceudelavanda.lavandaflow.shared.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/inventory")
public class CurrentStockController {

    private final GetCurrentStock getCurrentStock;

    @Operation(
        summary = "Retrieve current stock for an inventory item",
        description = "Returns current stock materialized from batch balances. Inactive catalog items remain queryable. "
            + "Zero-balance batches are omitted by default and included when includeZeroBalance is true. "
            + "Expired batches remain represented because expiration classification is outside this query."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Current stock retrieved successfully", content = @Content(schema = @Schema(implementation = CurrentStockResponse.class))),
        @ApiResponse(responseCode = "404", description = "Inventory item not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/items/{inventoryItemId}/stock")
    public ResponseEntity<CurrentStockResponse> getCurrentStock(
        @PathVariable UUID inventoryItemId,
        @Parameter(
            description = "Optional; defaults to false. When false, zero-balance batches are omitted. When true, they are included. totalCurrentQuantity is unaffected.",
            required = false,
            schema = @Schema(defaultValue = "false"),
            example = "false"
        )
        @RequestParam(defaultValue = "false") boolean includeZeroBalance
    ) {
        var result = getCurrentStock.execute(new GetCurrentStockQuery(inventoryItemId, includeZeroBalance));
        return ResponseEntity.ok(CurrentStockResponse.from(result));
    }
}
