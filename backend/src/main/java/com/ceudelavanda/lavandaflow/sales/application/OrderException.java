package com.ceudelavanda.lavandaflow.sales.application;

import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;
import java.util.Map;
import java.util.UUID;

/** Sales failures mapped through the existing transport-independent error contract. */
public class OrderException extends DomainException {
    private OrderException(String code, String message, ErrorType type, Map<String, String> details) {
        super(code, message, type, details);
    }
    public static OrderException invalid(String field, String message) {
        return new OrderException("VALIDATION_ERROR", "Invalid draft order", ErrorType.VALIDATION, Map.of(field, message));
    }
    public static OrderException notFound(UUID id) {
        return new OrderException("ORDER_NOT_FOUND", "Order not found", ErrorType.NOT_FOUND, Map.of("orderId", id.toString()));
    }
    public static OrderException notDraft() {
        return new OrderException("ORDER_NOT_EDITABLE", "Only draft orders can be edited", ErrorType.CONFLICT, null);
    }
    public static OrderException reference(String code, String field, UUID id) {
        return new OrderException(code, "Order reference is unavailable or ineligible", ErrorType.VALIDATION, Map.of(field, id.toString()));
    }
    public static OrderException search(String field, String message) {
        return new OrderException("INVALID_ORDER_SEARCH_QUERY", "Invalid order query", ErrorType.VALIDATION, Map.of(field, message));
    }
}
