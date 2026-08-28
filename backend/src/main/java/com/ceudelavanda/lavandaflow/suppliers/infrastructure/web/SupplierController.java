package com.ceudelavanda.lavandaflow.suppliers.infrastructure.web;

import com.ceudelavanda.lavandaflow.shared.error.ApiErrorResponse;
import com.ceudelavanda.lavandaflow.suppliers.application.GetSupplier;
import com.ceudelavanda.lavandaflow.suppliers.application.RegisterSupplier;
import com.ceudelavanda.lavandaflow.suppliers.application.RegisterSupplierCommand;
import com.ceudelavanda.lavandaflow.suppliers.application.SearchSuppliers;
import com.ceudelavanda.lavandaflow.suppliers.application.SupplierSearchQuery;
import com.ceudelavanda.lavandaflow.suppliers.infrastructure.web.request.RegisterSupplierRequest;
import com.ceudelavanda.lavandaflow.suppliers.infrastructure.web.response.SupplierPageResponse;
import com.ceudelavanda.lavandaflow.suppliers.infrastructure.web.response.SupplierResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/suppliers")
public class SupplierController {

    private final RegisterSupplier registerSupplier;
    private final GetSupplier getSupplier;
    private final SearchSuppliers searchSuppliers;

    @Operation(summary = "Register a supplier")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Supplier registered", content = @Content(schema = @Schema(implementation = SupplierResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request body", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<SupplierResponse> register(@Valid @RequestBody RegisterSupplierRequest request) {
        var result = registerSupplier.execute(new RegisterSupplierCommand(
            request.name(), request.identifier(), request.contact(), request.notes()
        ));
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .location(URI.create("/api/v1/suppliers/" + result.id()))
            .body(SupplierResponse.from(result));
    }

    @Operation(summary = "Retrieve a supplier")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Supplier retrieved", content = @Content(schema = @Schema(implementation = SupplierResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid supplier identifier", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Supplier not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{supplierId}")
    public ResponseEntity<SupplierResponse> getById(@PathVariable UUID supplierId) {
        return ResponseEntity.ok(SupplierResponse.from(getSupplier.execute(supplierId)));
    }

    @Operation(
        summary = "Search suppliers",
        description = "Filters are optional. Name matching is partial and case-insensitive. Results are ordered by name and then identifier."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Suppliers retrieved", content = @Content(schema = @Schema(implementation = SupplierPageResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid filters or pagination", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<SupplierPageResponse> search(
        @Parameter(description = "Optional partial, case-insensitive supplier name filter")
        @RequestParam(required = false) String name,
        @Parameter(description = "Optional active-state filter")
        @RequestParam(required = false) Boolean active,
        @Parameter(description = "Zero-based page number", schema = @Schema(defaultValue = "0", minimum = "0"))
        @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "Page size from 1 to 100", schema = @Schema(defaultValue = "20", minimum = "1", maximum = "100"))
        @RequestParam(defaultValue = "20") int size
    ) {
        var result = searchSuppliers.execute(new SupplierSearchQuery(name, active, page, size));
        return ResponseEntity.ok(SupplierPageResponse.from(result));
    }
}
