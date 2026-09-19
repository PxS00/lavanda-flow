package com.ceudelavanda.lavandaflow.inventory.infrastructure.web;

import com.ceudelavanda.lavandaflow.inventory.application.overview.GetInventoryStockList;
import com.ceudelavanda.lavandaflow.inventory.application.overview.InventoryStockListQuery;
import com.ceudelavanda.lavandaflow.inventory.infrastructure.config.InventoryAlertProperties;
import com.ceudelavanda.lavandaflow.inventory.infrastructure.web.response.InventoryStockListPageResponse;
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

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/inventory/items")
public class InventoryStockListController {

    private final GetInventoryStockList getInventoryStockList;
    private final InventoryAlertProperties inventoryAlertProperties;

    @Operation(summary = "Browse paginated operational stock")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Operational stock retrieved", content = @Content(schema = @Schema(implementation = InventoryStockListPageResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid category or pagination", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<InventoryStockListPageResponse> getStock(
        @Parameter(description = "Optional repeatable exact catalog category")
        @RequestParam(required = false) List<String> category,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        var result = getInventoryStockList.execute(new InventoryStockListQuery(
            category, page, size, inventoryAlertProperties.expirationWindowDays()
        ));
        return ResponseEntity.ok(InventoryStockListPageResponse.from(result));
    }
}
