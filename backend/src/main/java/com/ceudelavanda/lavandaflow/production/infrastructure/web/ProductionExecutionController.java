package com.ceudelavanda.lavandaflow.production.infrastructure.web;

import com.ceudelavanda.lavandaflow.production.application.execution.ProductionSourceAllocationCommand;
import com.ceudelavanda.lavandaflow.production.application.execution.RegisterProduction;
import com.ceudelavanda.lavandaflow.production.application.execution.RegisterProductionCommand;
import com.ceudelavanda.lavandaflow.production.application.history.GetProductionExecutionDetails;
import com.ceudelavanda.lavandaflow.production.application.history.GetProductionExecutionHistory;
import com.ceudelavanda.lavandaflow.production.application.history.GetProductionExecutionHistoryQuery;
import com.ceudelavanda.lavandaflow.production.infrastructure.web.request.RegisterProductionRequest;
import com.ceudelavanda.lavandaflow.production.infrastructure.web.response.ProductionExecutionDetailsResponse;
import com.ceudelavanda.lavandaflow.production.infrastructure.web.response.ProductionExecutionHistoryResponse;
import com.ceudelavanda.lavandaflow.production.infrastructure.web.response.ProductionExecutionResponse;
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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/production/executions")
public class ProductionExecutionController {

    private final RegisterProduction registerProduction;
    private final GetProductionExecutionHistory getProductionExecutionHistory;
    private final GetProductionExecutionDetails getProductionExecutionDetails;

    @Operation(
        summary = "List completed production executions",
        description = "Returns a zero-based page filtered by inclusive production dates and ordered by production date, completion instant, and execution ID, all descending. Page size is limited to 100."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Production execution history retrieved", content = @Content(schema = @Schema(implementation = ProductionExecutionHistoryResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid date range or pagination", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<ProductionExecutionHistoryResponse> list(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        var result = getProductionExecutionHistory.execute(
            new GetProductionExecutionHistoryQuery(from, to, page, size)
        );
        return ResponseEntity.ok(ProductionExecutionHistoryResponse.from(result));
    }

    @Operation(
        summary = "Get a completed production execution",
        description = "Returns persisted production facts and ordered source consumptions for the stable execution identity. Catalog names and units are current display metadata."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Production execution retrieved", content = @Content(schema = @Schema(implementation = ProductionExecutionDetailsResponse.class))),
        @ApiResponse(responseCode = "404", description = "Production execution not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{executionId}")
    public ResponseEntity<ProductionExecutionDetailsResponse> getById(
        @PathVariable UUID executionId
    ) {
        return ResponseEntity.ok(
            ProductionExecutionDetailsResponse.from(getProductionExecutionDetails.execute(executionId))
        );
    }

    @Operation(
        summary = "Register a completed production execution",
        description = "Validates exact source batches against the scaled formula, resolves the definitive internal lot code, applies inventory effects, and persists production traceability in one local transaction."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Production execution registered", content = @Content(schema = @Schema(implementation = ProductionExecutionResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid quantity, dates, or lot-code input", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Formula, catalog item, or source batch not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Formula allocation mismatch, inactive or incompatible catalog state, exhausted generated lot sequence, or ineligible/insufficient inventory stock", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<ProductionExecutionResponse> register(
        @Valid @RequestBody RegisterProductionRequest request
    ) {
        var result = registerProduction.execute(new RegisterProductionCommand(
            request.formulaId(),
            request.outputQuantity(),
            request.sourceAllocations().stream()
                .map(allocation -> new ProductionSourceAllocationCommand(
                    allocation.batchId(),
                    allocation.quantity()
                ))
                .toList(),
            request.productionDate(),
            request.outputReceivedAt(),
            request.outputExpiresAt(),
            request.lotCodeMode(),
            request.manualLotCode()
        ));
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ProductionExecutionResponse.from(result));
    }
}
