package com.ceudelavanda.lavandaflow.inventory.infrastructure.web;

import com.ceudelavanda.lavandaflow.inventory.application.GetCurrentStock;
import com.ceudelavanda.lavandaflow.inventory.application.ConfigureMinimumStockLevel;
import com.ceudelavanda.lavandaflow.inventory.application.DeleteMinimumStockLevel;
import com.ceudelavanda.lavandaflow.inventory.application.GetMinimumStockLevel;
import com.ceudelavanda.lavandaflow.inventory.application.RegisterStockEntry;
import com.ceudelavanda.lavandaflow.inventory.application.RegisterStockAdjustment;
import com.ceudelavanda.lavandaflow.inventory.application.RegisterStockWithdrawal;
import com.ceudelavanda.lavandaflow.inventory.application.RegisterFefoWithdrawal;
import com.ceudelavanda.lavandaflow.inventory.application.command.RegisterStockEntryCommand;
import com.ceudelavanda.lavandaflow.inventory.application.command.RegisterStockAdjustmentCommand;
import com.ceudelavanda.lavandaflow.inventory.application.command.RegisterStockWithdrawalCommand;
import com.ceudelavanda.lavandaflow.inventory.application.command.RegisterFefoWithdrawalCommand;
import com.ceudelavanda.lavandaflow.inventory.application.query.GetCurrentStockQuery;
import com.ceudelavanda.lavandaflow.inventory.infrastructure.web.request.RegisterStockEntryRequest;
import com.ceudelavanda.lavandaflow.inventory.infrastructure.web.request.MinimumStockLevelRequest;
import com.ceudelavanda.lavandaflow.inventory.infrastructure.web.request.RegisterStockAdjustmentRequest;
import com.ceudelavanda.lavandaflow.inventory.infrastructure.web.request.RegisterStockWithdrawalRequest;
import com.ceudelavanda.lavandaflow.inventory.infrastructure.web.request.RegisterFefoWithdrawalRequest;
import com.ceudelavanda.lavandaflow.inventory.infrastructure.web.response.RegisterStockEntryResponse;
import com.ceudelavanda.lavandaflow.inventory.infrastructure.web.response.RegisterStockAdjustmentResponse;
import com.ceudelavanda.lavandaflow.inventory.infrastructure.web.response.RegisterStockWithdrawalResponse;
import com.ceudelavanda.lavandaflow.inventory.infrastructure.web.response.RegisterFefoWithdrawalResponse;
import com.ceudelavanda.lavandaflow.inventory.infrastructure.web.response.CurrentStockResponse;
import com.ceudelavanda.lavandaflow.inventory.infrastructure.web.response.MinimumStockLevelResponse;
import com.ceudelavanda.lavandaflow.shared.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.net.URI;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final GetCurrentStock getCurrentStock;
    private final ConfigureMinimumStockLevel configureMinimumStockLevel;
    private final GetMinimumStockLevel getMinimumStockLevel;
    private final DeleteMinimumStockLevel deleteMinimumStockLevel;
    private final RegisterStockEntry registerStockEntry;
    private final RegisterStockAdjustment registerStockAdjustment;
    private final RegisterStockWithdrawal registerStockWithdrawal;
    private final RegisterFefoWithdrawal registerFefoWithdrawal;

    @Operation(
        summary = "Create or update an inventory minimum stock level",
        description = "Creates a positive, six-decimal minimum stock level for an inventory item or updates its existing level. "
            + "Inactive items may also be configured."
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
            return ResponseEntity.created(URI.create("/api/v1/inventory/items/" + inventoryItemId + "/minimum-stock-level"))
                .body(response);
        }

        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Retrieve an inventory minimum stock level",
        description = "Returns the configured minimum stock level for an existing inventory item. Inactive items remain queryable."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Minimum stock level retrieved", content = @Content(schema = @Schema(implementation = MinimumStockLevelResponse.class))),
        @ApiResponse(responseCode = "404", description = "Inventory item or minimum stock level not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/items/{inventoryItemId}/minimum-stock-level")
    public ResponseEntity<MinimumStockLevelResponse> getMinimumStockLevel(@PathVariable UUID inventoryItemId) {
        return ResponseEntity.ok(MinimumStockLevelResponse.from(getMinimumStockLevel.execute(inventoryItemId)));
    }

    @Operation(
        summary = "Delete an inventory minimum stock level",
        description = "Removes a configured minimum stock level. Deleting an already absent level is successful and returns no content."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Minimum stock level deleted or already absent"),
        @ApiResponse(responseCode = "404", description = "Inventory item not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @DeleteMapping("/items/{inventoryItemId}/minimum-stock-level")
    public ResponseEntity<Void> deleteMinimumStockLevel(@PathVariable UUID inventoryItemId) {
        deleteMinimumStockLevel.execute(inventoryItemId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Retrieve current stock for an inventory item",
        description = "Returns current stock materialized from batch balances. Inactive catalog items remain queryable. "
            + "Zero-balance batches are omitted by default and included when includeZeroBalance is true. "
            + "Expired batches remain represented because expiration classification is outside this query."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Current stock retrieved successfully",
            content = @Content(
                schema = @Schema(implementation = CurrentStockResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Inventory item not found",
            content = @Content(
                schema = @Schema(implementation = ApiErrorResponse.class)
            )
        )
    })
    @GetMapping("/items/{inventoryItemId}/stock")
    public ResponseEntity<CurrentStockResponse> getCurrentStock(
        @PathVariable UUID inventoryItemId,
        @Parameter(
            description = "Optional; defaults to false. When false, zero-balance batches are omitted. "
                + "When true, they are included. totalCurrentQuantity is unaffected.",
            required = false,
            schema = @Schema(defaultValue = "false"),
            example = "false"
        )
        @RequestParam(defaultValue = "false") boolean includeZeroBalance
    ) {
        var query = new GetCurrentStockQuery(inventoryItemId, includeZeroBalance);
        var result = getCurrentStock.execute(query);

        return ResponseEntity.ok(CurrentStockResponse.from(result));
    }

    @Operation(
        summary = "Register stock entry",
        description = "Adds a positive quantity to an existing batch and records the corresponding stock movement."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "Stock entry registered successfully",
            content = @Content(
                schema = @Schema(implementation = RegisterStockEntryResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request data",
            content = @Content(
                schema = @Schema(implementation = ApiErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Batch not found",
            content = @Content(
                schema = @Schema(implementation = ApiErrorResponse.class)
            )
        )
    })
    @PostMapping("/batches/{batchId}/entries")
    public ResponseEntity<RegisterStockEntryResponse> registerStockEntry(
        @PathVariable UUID batchId,
        @Valid @RequestBody RegisterStockEntryRequest request
    ) {
        var command = new RegisterStockEntryCommand(
            batchId,
            request.quantity(),
            request.reason()
        );

        var result = registerStockEntry.execute(command);
        var response = RegisterStockEntryResponse.from(result);

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(response);
    }

    @Operation(
        summary = "Register stock withdrawal",
        description = "Removes a positive quantity from the selected batch and records the corresponding stock movement."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "Stock withdrawal registered successfully",
            content = @Content(
                schema = @Schema(implementation = RegisterStockWithdrawalResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request data",
            content = @Content(
                schema = @Schema(implementation = ApiErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Batch not found",
            content = @Content(
                schema = @Schema(implementation = ApiErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "422",
            description = "Insufficient stock",
            content = @Content(
                schema = @Schema(implementation = ApiErrorResponse.class)
            )
        )
    })
    @PostMapping("/batches/{batchId}/withdrawals")
    public ResponseEntity<RegisterStockWithdrawalResponse> registerStockWithdrawal(
        @PathVariable UUID batchId,
        @Valid @RequestBody RegisterStockWithdrawalRequest request
    ) {
        var command = new RegisterStockWithdrawalCommand(
            batchId,
            request.quantity(),
            request.reason()
        );

        var result = registerStockWithdrawal.execute(command);
        var response = RegisterStockWithdrawalResponse.from(result);

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(response);
    }

    @Operation(
        summary = "Register automatic FEFO stock withdrawal",
        description = "Automatically allocates a withdrawal across eligible batches using FEFO. "
            + "Batches expiring today are considered expired. Multiple batches may be consumed, "
            + "and no partial withdrawal is committed when eligible stock is insufficient."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "FEFO stock withdrawal registered successfully",
            content = @Content(
                schema = @Schema(implementation = RegisterFefoWithdrawalResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request data",
            content = @Content(
                schema = @Schema(implementation = ApiErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Inventory item not found",
            content = @Content(
                schema = @Schema(implementation = ApiErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "422",
            description = "Inventory item is inactive or eligible stock is insufficient",
            content = @Content(
                schema = @Schema(implementation = ApiErrorResponse.class)
            )
        )
    })
    @PostMapping("/items/{inventoryItemId}/withdrawals")
    public ResponseEntity<RegisterFefoWithdrawalResponse> registerFefoWithdrawal(
        @PathVariable UUID inventoryItemId,
        @Valid @RequestBody RegisterFefoWithdrawalRequest request
    ) {
        var command = new RegisterFefoWithdrawalCommand(
            inventoryItemId,
            request.quantity(),
            request.reason()
        );

        var result = registerFefoWithdrawal.execute(command);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(RegisterFefoWithdrawalResponse.from(result));
    }

    @Operation(
        summary = "Register stock adjustment",
        description = "Applies a signed adjustment to the selected batch and records the corresponding stock movement."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "Stock adjustment registered successfully",
            content = @Content(
                schema = @Schema(implementation = RegisterStockAdjustmentResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request data or zero adjustment",
            content = @Content(
                schema = @Schema(implementation = ApiErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Batch not found",
            content = @Content(
                schema = @Schema(implementation = ApiErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "422",
            description = "Insufficient stock",
            content = @Content(
                schema = @Schema(implementation = ApiErrorResponse.class)
            )
        )
    })
    @PostMapping("/batches/{batchId}/adjustments")
    public ResponseEntity<RegisterStockAdjustmentResponse> registerStockAdjustment(
        @PathVariable UUID batchId,
        @Valid @RequestBody RegisterStockAdjustmentRequest request
    ) {
        var command = new RegisterStockAdjustmentCommand(
            batchId,
            request.quantity(),
            request.reason()
        );

        var result = registerStockAdjustment.execute(command);
        var response = RegisterStockAdjustmentResponse.from(result);

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(response);
    }
}
