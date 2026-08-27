package com.ceudelavanda.lavandaflow.inventory.infrastructure.web;

import com.ceudelavanda.lavandaflow.inventory.application.GetExpirationAlerts;
import com.ceudelavanda.lavandaflow.inventory.application.GetLowStockAlerts;
import com.ceudelavanda.lavandaflow.inventory.application.query.GetExpirationAlertsQuery;
import com.ceudelavanda.lavandaflow.inventory.infrastructure.config.InventoryAlertProperties;
import com.ceudelavanda.lavandaflow.inventory.infrastructure.web.response.ExpirationAlertsResponse;
import com.ceudelavanda.lavandaflow.inventory.infrastructure.web.response.LowStockAlertsResponse;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/inventory/alerts")
public class InventoryAlertController {

    private final GetExpirationAlerts getExpirationAlerts;
    private final GetLowStockAlerts getLowStockAlerts;
    private final InventoryAlertProperties inventoryAlertProperties;

    @Operation(
        summary = "Retrieve inventory low-stock alerts",
        description = "Returns active items with configured minimum stock levels when available non-expired stock is below the minimum. "
            + "Expired batches, batches expiring today, and zero balances are excluded from availability. Inactive items do not generate alerts."
    )
    @ApiResponse(responseCode = "200", description = "Low-stock alerts retrieved successfully", content = @Content(schema = @Schema(implementation = LowStockAlertsResponse.class)))
    @GetMapping("/low-stock")
    public ResponseEntity<LowStockAlertsResponse> getLowStockAlerts() {
        return ResponseEntity.ok(LowStockAlertsResponse.from(getLowStockAlerts.execute()));
    }

    @Operation(
        summary = "Retrieve inventory expiration alerts",
        description = "Returns positive-balance batches that are expired or approaching expiration. "
            + "A batch is EXPIRED when expiresAt is on or before asOfDate, so a batch expiring today is already expired. "
            + "A future batch is EXPIRING_SOON when expiresAt is on or before the inclusive alert cutoff. "
            + "Batches without an expiration date and zero-balance batches are excluded. "
            + "The alert is informational and does not define stock-consumption eligibility."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Expiration alerts retrieved successfully",
            content = @Content(schema = @Schema(implementation = ExpirationAlertsResponse.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid negative expiration alert window",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        )
    })
    @GetMapping("/expiration")
    public ResponseEntity<ExpirationAlertsResponse> getExpirationAlerts(
        @Parameter(
            description = "Optional non-negative future alert window in days. When omitted, the configured application default is used. "
                + "Expired batches remain included regardless of the window. A value of 0 returns expired batches only.",
            required = false,
            schema = @Schema(minimum = "0"),
            example = "30"
        )
        @RequestParam(required = false) Integer windowDays
    ) {
        var resolvedWindowDays = windowDays != null
            ? windowDays
            : inventoryAlertProperties.expirationWindowDays();
        var query = new GetExpirationAlertsQuery(resolvedWindowDays);
        var result = getExpirationAlerts.execute(query);

        return ResponseEntity.ok(ExpirationAlertsResponse.from(result));
    }
}
