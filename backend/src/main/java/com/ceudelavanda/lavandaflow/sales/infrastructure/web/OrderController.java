package com.ceudelavanda.lavandaflow.sales.infrastructure.web;

import com.ceudelavanda.lavandaflow.sales.application.*;
import com.ceudelavanda.lavandaflow.shared.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/sales")
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "Invalid draft, reference, identifier or query", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(responseCode = "401", description = "Session authentication required", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(responseCode = "403", description = "OPERATOR authorization and valid CSRF on writes required", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class OrderController {
    private final OrderManagement orders;

    @PostMapping
    @Operation(summary = "Create a draft order", description = "Optional active customer and one or more unique eligible products. Exact BRL prices, line HALF_UP rounding and authoritative total. No stock check or effect.")
    @ApiResponse(responseCode = "201", description = "Draft registered", content = @Content(schema = @Schema(implementation = OrderResult.class)))
    public ResponseEntity<OrderResult> register(@RequestBody SaveDraftRequest request) {
        var order = orders.register(request.toCommand());
        return ResponseEntity.created(URI.create("/api/v1/sales/" + order.id())).body(order);
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Inspect an order or confirmed sale", description = "Drafts return live labels; confirmed sales return immutable confirmation-time customer/product snapshots and exact batch/movement allocations.")
    @ApiResponse(responseCode = "404", description = "Draft not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public OrderResult getById(@PathVariable UUID orderId) { return orders.getById(orderId); }

    @PutMapping("/{orderId}")
    @Operation(summary = "Replace a draft order", description = "Preserves order identity and creation time. Retained items preserve line UUIDs; explicit line UUIDs must belong to the same item/order. Removed lines are removed. No stock effects.")
    @ApiResponse(responseCode = "404", description = "Order not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Order is no longer a draft", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public OrderResult update(@PathVariable UUID orderId, @RequestBody SaveDraftRequest request) { return orders.update(orderId, request.toCommand()); }

    @PostMapping("/{orderId}/confirm")
    @Operation(summary = "Confirm a draft with atomic FEFO withdrawal", description = "Order UUID is the idempotency key. Locks the order and joins inventory in one PostgreSQL transaction. Confirmed retries return persisted history without new stock effects. No reservation, automatic retry or physical return. Any failure leaves the draft unchanged.")
    @ApiResponse(responseCode = "200", description = "Persisted confirmed result including snapshots and exact batch/movement allocations")
    @ApiResponse(responseCode = "404", description = "Order not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Order cannot be confirmed or lock conflict", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "422", description = "INSUFFICIENT_ELIGIBLE_STOCK or ineligible inventory item; complete rollback", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "500", description = "ORDER_PERSISTENCE_FAILED; sanitized persistence or transaction failure", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public OrderResult confirm(@PathVariable UUID orderId) { return orders.confirm(orderId); }

    @PostMapping("/{orderId}/cancel")
    @Operation(summary = "Cancel an unconfirmed draft", description = "DRAFT only; no stock effects. Confirmed sales cannot be cancelled, edited or deleted; physical returns require a separate audited operation.")
    @ApiResponse(responseCode = "409", description = "Order is not a draft", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public OrderResult cancel(@PathVariable UUID orderId) { return orders.cancel(orderId); }

    @GetMapping
    @Operation(summary = "Search draft orders", description = "Literal partial UUID q, optional customerId and inclusive UTC creation dates from/to. DRAFT only; newest creation first, UUID ascending for ties.")
    public OrderManagement.Page search(@RequestParam(required = false) String q,
        @RequestParam(required = false) UUID customerId,
        @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
        @Parameter(schema = @Schema(defaultValue = "0", minimum = "0")) @RequestParam(defaultValue = "0") int page,
        @Parameter(schema = @Schema(defaultValue = "20", minimum = "1", maximum = "100")) @RequestParam(defaultValue = "20") int size) {
        return orders.search(new OrderSearchQuery(q, customerId, from, to, page, size));
    }
}
