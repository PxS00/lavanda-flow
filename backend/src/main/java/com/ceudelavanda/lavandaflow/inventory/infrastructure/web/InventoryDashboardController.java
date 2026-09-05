package com.ceudelavanda.lavandaflow.inventory.infrastructure.web;

import com.ceudelavanda.lavandaflow.inventory.application.dashboard.GetOperationalDashboard;
import com.ceudelavanda.lavandaflow.inventory.infrastructure.config.InventoryAlertProperties;
import com.ceudelavanda.lavandaflow.inventory.infrastructure.web.response.InventoryDashboardResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/inventory/dashboard")
public class InventoryDashboardController {

    private final GetOperationalDashboard getOperationalDashboard;
    private final InventoryAlertProperties inventoryAlertProperties;

    @Operation(
        summary = "Retrieve the operational inventory dashboard",
        description = "Returns item-based active, low-stock, and out-of-stock counters plus batch-based expiration counters. "
            + "Expired batches and batches expiring on asOfDate are unavailable for low-stock and out-of-stock evaluation. "
            + "Expiring-soon includes future expirations through the inclusive configured cutoff."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Operational dashboard retrieved successfully",
        content = @Content(schema = @Schema(implementation = InventoryDashboardResponse.class))
    )
    @GetMapping
    public ResponseEntity<InventoryDashboardResponse> getDashboard() {
        var result = getOperationalDashboard.execute(inventoryAlertProperties.expirationWindowDays());
        return ResponseEntity.ok(InventoryDashboardResponse.from(result));
    }
}
