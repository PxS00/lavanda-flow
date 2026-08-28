package com.ceudelavanda.lavandaflow.inventory.infrastructure.web;

import com.ceudelavanda.lavandaflow.inventory.application.movement.RegisterStockAdjustment;
import com.ceudelavanda.lavandaflow.inventory.application.movement.RegisterStockAdjustmentCommand;
import com.ceudelavanda.lavandaflow.inventory.application.movement.RegisterStockEntry;
import com.ceudelavanda.lavandaflow.inventory.application.movement.RegisterStockEntryCommand;
import com.ceudelavanda.lavandaflow.inventory.application.movement.RegisterStockWithdrawal;
import com.ceudelavanda.lavandaflow.inventory.application.movement.RegisterStockWithdrawalCommand;
import com.ceudelavanda.lavandaflow.inventory.infrastructure.web.request.RegisterStockAdjustmentRequest;
import com.ceudelavanda.lavandaflow.inventory.infrastructure.web.request.RegisterStockEntryRequest;
import com.ceudelavanda.lavandaflow.inventory.infrastructure.web.request.RegisterStockWithdrawalRequest;
import com.ceudelavanda.lavandaflow.inventory.infrastructure.web.response.RegisterStockAdjustmentResponse;
import com.ceudelavanda.lavandaflow.inventory.infrastructure.web.response.RegisterStockEntryResponse;
import com.ceudelavanda.lavandaflow.inventory.infrastructure.web.response.RegisterStockWithdrawalResponse;
import com.ceudelavanda.lavandaflow.shared.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/inventory")
public class StockMovementController {

    private final RegisterStockEntry registerStockEntry;
    private final RegisterStockAdjustment registerStockAdjustment;
    private final RegisterStockWithdrawal registerStockWithdrawal;

    @Operation(summary = "Register stock entry", description = "Adds a positive quantity to an existing batch and records the corresponding stock movement.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Stock entry registered successfully", content = @Content(schema = @Schema(implementation = RegisterStockEntryResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request data", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Batch not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/batches/{batchId}/entries")
    public ResponseEntity<RegisterStockEntryResponse> registerStockEntry(
        @PathVariable UUID batchId,
        @Valid @RequestBody RegisterStockEntryRequest request
    ) {
        var result = registerStockEntry.execute(new RegisterStockEntryCommand(batchId, request.quantity(), request.reason()));
        return ResponseEntity.status(HttpStatus.CREATED).body(RegisterStockEntryResponse.from(result));
    }

    @Operation(summary = "Register stock withdrawal", description = "Removes a positive quantity from the selected batch and records the corresponding stock movement.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Stock withdrawal registered successfully", content = @Content(schema = @Schema(implementation = RegisterStockWithdrawalResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request data", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Batch not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Insufficient stock", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/batches/{batchId}/withdrawals")
    public ResponseEntity<RegisterStockWithdrawalResponse> registerStockWithdrawal(
        @PathVariable UUID batchId,
        @Valid @RequestBody RegisterStockWithdrawalRequest request
    ) {
        var result = registerStockWithdrawal.execute(new RegisterStockWithdrawalCommand(batchId, request.quantity(), request.reason()));
        return ResponseEntity.status(HttpStatus.CREATED).body(RegisterStockWithdrawalResponse.from(result));
    }

    @Operation(summary = "Register stock adjustment", description = "Applies a signed adjustment to the selected batch and records the corresponding stock movement.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Stock adjustment registered successfully", content = @Content(schema = @Schema(implementation = RegisterStockAdjustmentResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request data or zero adjustment", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Batch not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Insufficient stock", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/batches/{batchId}/adjustments")
    public ResponseEntity<RegisterStockAdjustmentResponse> registerStockAdjustment(
        @PathVariable UUID batchId,
        @Valid @RequestBody RegisterStockAdjustmentRequest request
    ) {
        var result = registerStockAdjustment.execute(new RegisterStockAdjustmentCommand(batchId, request.quantity(), request.reason()));
        return ResponseEntity.status(HttpStatus.CREATED).body(RegisterStockAdjustmentResponse.from(result));
    }
}
