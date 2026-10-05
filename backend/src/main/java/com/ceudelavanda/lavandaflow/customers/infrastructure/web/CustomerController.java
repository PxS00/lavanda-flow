package com.ceudelavanda.lavandaflow.customers.infrastructure.web;

import com.ceudelavanda.lavandaflow.customers.application.CustomerManagement;
import com.ceudelavanda.lavandaflow.customers.application.CustomerSearchQuery;
import com.ceudelavanda.lavandaflow.customers.domain.CustomerContact;
import com.ceudelavanda.lavandaflow.customers.infrastructure.web.request.CustomerRequest;
import com.ceudelavanda.lavandaflow.customers.infrastructure.web.response.CustomerPageResponse;
import com.ceudelavanda.lavandaflow.customers.infrastructure.web.response.CustomerResponse;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.net.URI;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/customers")
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "Invalid contact, identifier, filters or paging", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(responseCode = "401", description = "Session authentication required", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(responseCode = "403", description = "Missing or invalid CSRF token on writes", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class CustomerController {
    private final CustomerManagement customers;

    @Operation(summary = "Register an active customer contact", description = "A name-only contact is valid. Phone and email are independently optional and are not unique.")
    @ApiResponse(responseCode = "201", description = "Customer registered", content = @Content(schema = @Schema(implementation = CustomerResponse.class)))
    @PostMapping
    public ResponseEntity<CustomerResponse> register(@RequestBody CustomerRequest request) {
        var result = customers.register(new CustomerContact(request.name(), request.phone(), request.email()));
        return ResponseEntity.created(URI.create("/api/v1/customers/" + result.id())).body(CustomerResponse.from(result));
    }

    @Operation(summary = "Retrieve an active or inactive customer")
    @ApiResponse(responseCode = "404", description = "Customer not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @GetMapping("/{customerId}")
    public CustomerResponse getById(@PathVariable UUID customerId) {
        return CustomerResponse.from(customers.getById(customerId));
    }

    @Operation(summary = "Replace customer contact fields", description = "Preserves ID, creation timestamp and active state. Omitted or blank optional values are cleared.")
    @ApiResponse(responseCode = "404", description = "Customer not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @PutMapping("/{customerId}")
    public CustomerResponse update(@PathVariable UUID customerId, @RequestBody CustomerRequest request) {
        return CustomerResponse.from(customers.update(customerId, new CustomerContact(request.name(), request.phone(), request.email())));
    }

    @Operation(summary = "Activate a customer", description = "Idempotent explicit reactivation; retains identity and contact data.")
    @ApiResponse(responseCode = "404", description = "Customer not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @PostMapping("/{customerId}/activate")
    public CustomerResponse activate(@PathVariable UUID customerId) {
        return CustomerResponse.from(customers.changeActiveState(customerId, true));
    }

    @Operation(summary = "Deactivate a customer", description = "Idempotent; retains the row and all future historical references. No hard deletion is exposed.")
    @ApiResponse(responseCode = "404", description = "Customer not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @PostMapping("/{customerId}/deactivate")
    public CustomerResponse deactivate(@PathVariable UUID customerId) {
        return CustomerResponse.from(customers.changeActiveState(customerId, false));
    }

    @Operation(summary = "Search customer contacts", description = "Optional partial search across name/email (case-insensitive) and phone (same separator normalization as input). Blank q lists normally. Literal LIKE characters are escaped. Ordered by name, then ID.")
    @GetMapping
    public CustomerPageResponse search(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) Boolean active,
        @Parameter(schema = @Schema(defaultValue = "0", minimum = "0")) @RequestParam(defaultValue = "0") int page,
        @Parameter(schema = @Schema(defaultValue = "20", minimum = "1", maximum = "100")) @RequestParam(defaultValue = "20") int size
    ) {
        return CustomerPageResponse.from(customers.search(new CustomerSearchQuery(q, active, page, size)));
    }
}
