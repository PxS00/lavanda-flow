package com.ceudelavanda.lavandaflow.inventory.infrastructure.web;

import com.ceudelavanda.lavandaflow.inventory.application.receipt.RegisterStockReceipt;
import com.ceudelavanda.lavandaflow.inventory.application.receipt.RegisterStockReceiptCommand;
import com.ceudelavanda.lavandaflow.inventory.infrastructure.web.request.RegisterStockReceiptRequest;
import com.ceudelavanda.lavandaflow.inventory.infrastructure.web.response.RegisterStockReceiptResponse;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/inventory")
public class StockReceiptController {

    private final RegisterStockReceipt registerStockReceipt;

    @Operation(
        summary = "Register stock receipt",
        description = "Creates a new inventory batch and exactly one immutable initial ENTRY movement in the same transaction. The inventory item and optional supplier must exist and be active."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Stock receipt registered successfully", content = @Content(schema = @Schema(implementation = RegisterStockReceiptResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request, quantity precision, lot code, or batch date data", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Inventory item or supplier not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Inventory item or supplier is inactive", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/receipts")
    public ResponseEntity<RegisterStockReceiptResponse> registerStockReceipt(
        @Valid @RequestBody RegisterStockReceiptRequest request
    ) {
        var result = registerStockReceipt.execute(new RegisterStockReceiptCommand(
            request.inventoryItemId(),
            request.supplierId(),
            request.lotCode(),
            request.quantity(),
            request.receivedAt(),
            request.expiresAt(),
            request.reason()
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(RegisterStockReceiptResponse.from(result));
    }
}
