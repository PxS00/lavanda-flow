package com.ceudelavanda.lavandaflow.sales.application;

import com.ceudelavanda.lavandaflow.catalog.InventoryItemDetailsLookup;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.customers.CustomerLookup;
import com.ceudelavanda.lavandaflow.sales.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/** Draft transaction boundary. Validates live public references and exact decimals on every save.
 * Locks edits, preserves identities, calculates line-rounded BRL totals, and never calls inventory. */
@Service
@RequiredArgsConstructor
public class OrderManagement {
    private final OrderRepository repository;
    private final OrderQuery query;
    private final CustomerLookup customers;
    private final InventoryItemDetailsLookup catalog;
    private final Clock clock;

    /**
     * Registers a draft with server-assigned order and line identities and exact BRL totals.
     * A customer may be absent; all supplied references must be currently active and eligible.
     * Quantity and price are never rounded. Each extended amount is rounded HALF_UP to cents
     * before summation, with storage overflow rejected. No stock availability is checked.
     * @throws OrderException when lines, references, precision or calculated totals are invalid
     */
    @Transactional
    public OrderResult register(SaveDraftCommand command) {
        return result(repository.save(draft(UUID.randomUUID(), null, command)));
    }

    /**
     * Full replacement under the order row lock, retaining creation time and retained-item line IDs.
     * Omitted lines are removed; an omitted customer clears its association. Validation and
     * persistence share one transaction, so a failed replacement leaves the stored draft unchanged.
     * @throws OrderException when the order is missing, not editable or the replacement is invalid
     */
    @Transactional
    public OrderResult update(UUID id, SaveDraftCommand command) {
        var existing = repository.findByIdForUpdate(id).orElseThrow(() -> OrderException.notFound(id));
        if (existing.status() != OrderStatus.DRAFT) throw OrderException.notDraft();
        return result(repository.save(draft(id, existing, command)));
    }

    /**
     * Inspects stored draft values, including drafts with references that have since become inactive.
     * Customer and product labels are current lookup values, not confirmation-time history.
     * @throws OrderException when the identity does not identify a draft
     */
    @Transactional(readOnly = true)
    public OrderResult getById(UUID id) {
        var order = repository.findById(id).filter(o -> o.status() == OrderStatus.DRAFT)
            .orElseThrow(() -> OrderException.notFound(id));
        return result(order);
    }

    /** Returns a bounded, deterministic draft page with current labels and stored commercial values. */
    @Transactional(readOnly = true)
    public Page search(OrderSearchQuery search) {
        var page = query.search(search);
        return new Page(page.content().stream().map(this::result).toList(), page.page(), page.size(), page.totalElements(), page.totalPages());
    }

    public record Page(List<OrderResult> content, int page, int size, long totalElements, int totalPages) {}

    private Order draft(UUID id, Order existing, SaveDraftCommand command) {
        if (command.customerId() != null) {
            var customer = customers.findById(command.customerId()).orElseThrow(() ->
                OrderException.reference("ORDER_CUSTOMER_NOT_FOUND", "customerId", command.customerId()));
            if (!customer.active()) throw OrderException.reference("ORDER_CUSTOMER_INACTIVE", "customerId", customer.id());
        }
        if (command.lines() == null || command.lines().isEmpty()) throw OrderException.invalid("lines", "at least one line required");
        var existingLines = existing == null ? Map.<UUID, OrderLine>of() : existing.lines().stream()
            .collect(java.util.stream.Collectors.toMap(OrderLine::itemId, line -> line));
        var requestedItems = command.lines().stream().filter(Objects::nonNull).map(SaveDraftCommand.Line::itemId)
            .filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        var details = catalog.findByIds(requestedItems).stream()
            .collect(java.util.stream.Collectors.toMap(item -> item.id(), item -> item));
        var items = new HashSet<UUID>();
        var lines = new ArrayList<OrderLine>();
        var total = BigDecimal.ZERO.setScale(2);
        for (int i = 0; i < command.lines().size(); i++) {
            var line = command.lines().get(i);
            var field = "lines[" + i + "]";
            if (line == null || line.itemId() == null) throw OrderException.invalid(field + ".itemId", "required");
            if (!items.add(line.itemId())) throw OrderException.invalid(field + ".itemId", "duplicate item");
            var item = details.get(line.itemId());
            if (item == null) throw OrderException.reference("ORDER_ITEM_NOT_FOUND", field + ".itemId", line.itemId());
            if (!item.active() || !"FINISHED_PRODUCT".equals(item.category()) ||
                (item.unitOfMeasure() != UnitOfMeasure.MILLILITER && item.unitOfMeasure() != UnitOfMeasure.UNIT)) {
                throw OrderException.reference("ORDER_ITEM_INELIGIBLE", field + ".itemId", line.itemId());
            }
            var previous = Optional.ofNullable(existingLines.get(line.itemId()));
            if (line.id() != null && (previous.isEmpty() || !previous.get().id().equals(line.id()))) {
                throw OrderException.invalid(field + ".id", "must identify the existing line for this item");
            }
            decimal(line.quantity(), 13, 6, true, field + ".quantity");
            decimal(line.unitPrice(), 15, 4, false, field + ".unitPrice");
            var amount = line.quantity().multiply(line.unitPrice()).setScale(2, RoundingMode.HALF_UP);
            decimal(amount, 17, 2, false, field + ".amount");
            total = total.add(amount);
            decimal(total, 17, 2, false, "total");
            lines.add(new OrderLine(previous.map(OrderLine::id).orElseGet(UUID::randomUUID), line.itemId(),
                line.quantity().setScale(6), line.unitPrice().setScale(4), amount));
        }
        var now = Instant.now(clock).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        return new Order(id, command.customerId(), OrderStatus.DRAFT, lines, total,
            existing == null ? now : existing.createdAt(), now);
    }

    private static void decimal(BigDecimal value, int integer, int fraction, boolean positive, String field) {
        if (value == null || value.signum() < 0 || (positive && value.signum() == 0)
            || value.scale() < -integer || value.scale() > fraction || value.precision() - value.scale() > integer) {
            throw OrderException.invalid(field, "invalid sign, precision or numeric overflow");
        }
    }

    private OrderResult result(Order order) {
        var name = order.customerId() == null ? null : customers.findById(order.customerId()).map(c -> c.name()).orElse(null);
        var details = catalog.findByIds(order.lines().stream().map(OrderLine::itemId).toList()).stream()
            .collect(java.util.stream.Collectors.toMap(item -> item.id(), item -> item));
        var lines = order.lines().stream().map(line -> {
            var item = Optional.ofNullable(details.get(line.itemId()));
            return new OrderResult.Line(line.id(), line.itemId(), item.map(i -> i.name()).orElse(null),
                item.map(i -> i.unitOfMeasure()).orElse(null), line.quantity(), line.unitPrice(), line.amount());
        }).toList();
        return new OrderResult(order.id(), order.customerId(), name, order.status(), "BRL", lines, order.total(), order.createdAt(), order.updatedAt());
    }
}
