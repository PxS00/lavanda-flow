package com.ceudelavanda.lavandaflow.inventory.infrastructure.web;

import com.ceudelavanda.lavandaflow.inventory.application.overview.GetInventoryItemOverview;
import com.ceudelavanda.lavandaflow.inventory.application.overview.GetInventoryItemOverviewQuery;
import com.ceudelavanda.lavandaflow.inventory.infrastructure.config.InventoryAlertProperties;
import com.ceudelavanda.lavandaflow.inventory.infrastructure.web.response.InventoryItemOverviewResponse;
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
@RequestMapping("/api/v1/inventory/items")
public class InventoryItemOverviewController {

    private final GetInventoryItemOverview getInventoryItemOverview;
    private final InventoryAlertProperties inventoryAlertProperties;

    @Operation(
        summary = "Retrieve an inventory item operational overview",
        description = "Returns catalog identity together with physical and available stock, minimum-stock state, "
            + "positive-balance batch count, and expiration indicators. Physical total includes expired balances, "
            + "while available stock excludes expired and zero balances. A batch expiring on asOfDate is expired. "
            + "nearestExpiration is the earliest future expiration among positive-balance batches."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Inventory item overview retrieved successfully", content = @Content(schema = @Schema(implementation = InventoryItemOverviewResponse.class))),
        @ApiResponse(responseCode = "400", description = "Malformed inventory item identifier", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Inventory item not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{inventoryItemId}/overview")
    public ResponseEntity<InventoryItemOverviewResponse> getOverview(@PathVariable UUID inventoryItemId) {
        var result = getInventoryItemOverview.execute(new GetInventoryItemOverviewQuery(
            inventoryItemId,
            inventoryAlertProperties.expirationWindowDays()
        ));
        return ResponseEntity.ok(InventoryItemOverviewResponse.from(result));
    }
}
