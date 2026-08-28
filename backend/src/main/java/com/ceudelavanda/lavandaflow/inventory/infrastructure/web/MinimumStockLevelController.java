package com.ceudelavanda.lavandaflow.inventory.infrastructure.web;

import com.ceudelavanda.lavandaflow.inventory.application.minimumstock.ConfigureMinimumStockLevel;
import com.ceudelavanda.lavandaflow.inventory.application.minimumstock.DeleteMinimumStockLevel;
import com.ceudelavanda.lavandaflow.inventory.application.minimumstock.GetMinimumStockLevel;
import com.ceudelavanda.lavandaflow.inventory.infrastructure.web.request.MinimumStockLevelRequest;
import com.ceudelavanda.lavandaflow.inventory.infrastructure.web.response.MinimumStockLevelResponse;
import com.ceudelavanda.lavandaflow.shared.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/inventory")
public class MinimumStockLevelController {

    private final ConfigureMinimumStockLevel configureMinimumStockLevel;
    private final GetMinimumStockLevel getMinimumStockLevel;
    private final DeleteMinimumStockLevel deleteMinimumStockLevel;

    @Operation(
        summary = "Create or update an inventory minimum stock level",
        description = "Creates a positive, six-decimal minimum stock level for an inventory item or updates its existing level. Inactive items may also be configured."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Minimum stock level created", content = @Content(schema = @Schema(implementation = MinimumStockLevelResponse.class))),
        @ApiResponse(responseCode = "200", description = "Minimum stock level updated", content = @Content(schema = @Schema(implementation = MinimumStockLevelResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid minimum quantity", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Inventory item not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PutMapping("/items/{inventoryItemId}/minimum-stock-level")
    public ResponseEntity<MinimumStockLevelResponse> configureMinimumStockLevel(
        @PathVariable UUID inventoryItemId,
        @Valid @RequestBody MinimumStockLevelRequest request
    ) {
        var result = configureMinimumStockLevel.execute(inventoryItemId, request.minimumQuantity());
        var response = MinimumStockLevelResponse.from(result.level());
        if (result.created()) {
            return ResponseEntity.created(URI.create("/api/v1/inventory/items/" + inventoryItemId + "/minimum-stock-level")).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Retrieve an inventory minimum stock level", description = "Returns the configured minimum stock level for an existing inventory item. Inactive items remain queryable.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Minimum stock level retrieved", content = @Content(schema = @Schema(implementation = MinimumStockLevelResponse.class))),
        @ApiResponse(responseCode = "404", description = "Inventory item or minimum stock level not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/items/{inventoryItemId}/minimum-stock-level")
    public ResponseEntity<MinimumStockLevelResponse> getMinimumStockLevel(@PathVariable UUID inventoryItemId) {
        return ResponseEntity.ok(MinimumStockLevelResponse.from(getMinimumStockLevel.execute(inventoryItemId)));
    }

    @Operation(summary = "Delete an inventory minimum stock level", description = "Removes a configured minimum stock level. Deleting an already absent level is successful and returns no content.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Minimum stock level deleted or already absent"),
        @ApiResponse(responseCode = "404", description = "Inventory item not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @DeleteMapping("/items/{inventoryItemId}/minimum-stock-level")
    public ResponseEntity<Void> deleteMinimumStockLevel(@PathVariable UUID inventoryItemId) {
        deleteMinimumStockLevel.execute(inventoryItemId);
        return ResponseEntity.noContent().build();
    }
}
