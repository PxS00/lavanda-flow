package com.ceudelavanda.lavandaflow.sales.application;

import com.ceudelavanda.lavandaflow.catalog.*;
import com.ceudelavanda.lavandaflow.customers.*;
import com.ceudelavanda.lavandaflow.sales.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderManagementTest {
    private static final Instant NOW = Instant.parse("2026-10-06T12:00:00Z");
    private final UUID item = UUID.randomUUID();
    private OrderRepository repository;
    private OrderQuery query;
    private CustomerLookup customers;
    private InventoryItemDetailsLookup catalog;
    private OrderManagement orders;

    @BeforeEach void setUp() {
        repository = mock(OrderRepository.class); customers = mock(CustomerLookup.class); catalog = mock(InventoryItemDetailsLookup.class);
        query = mock(OrderQuery.class);
        orders = new OrderManagement(repository, query, customers, catalog, Clock.fixed(NOW, ZoneOffset.UTC));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(catalog.findByIds(any())).thenAnswer(invocation -> {
            java.util.Collection<UUID> ids = invocation.getArgument(0);
            return ids.stream().map(id -> catalog.findById(id)).flatMap(Optional::stream).toList();
        });
        when(catalog.findById(item)).thenReturn(Optional.of(new InventoryItemDetails(item, "Product", "FINISHED_PRODUCT", UnitOfMeasure.UNIT, true)));
    }
    private SaveDraftCommand.Line line(UUID itemId, String quantity, String price) {
        return new SaveDraftCommand.Line(null, itemId, quantity == null ? null : new BigDecimal(quantity), price == null ? null : new BigDecimal(price));
    }
    private OrderResult save(String quantity, String price) { return orders.register(new SaveDraftCommand(null, List.of(line(item, quantity, price)))); }

    @Test void searchResolvesAllPageLabelsInSingleBulkLookupCalls() {
        var secondItem = UUID.randomUUID();
        var firstCustomer = UUID.randomUUID();
        var secondCustomer = UUID.randomUUID();
        var first = new Order(UUID.randomUUID(), firstCustomer, OrderStatus.DRAFT,
            List.of(new OrderLine(UUID.randomUUID(), item, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE)),
            BigDecimal.ONE, NOW, NOW);
        var second = new Order(UUID.randomUUID(), secondCustomer, OrderStatus.DRAFT,
            List.of(new OrderLine(UUID.randomUUID(), secondItem, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN)),
            BigDecimal.TEN, NOW, NOW);
        when(query.search(any())).thenReturn(new OrderPage(List.of(first, second), 0, 20, 2, 1));
        var secondDetails = new InventoryItemDetails(secondItem, "Second", "FINISHED_PRODUCT", UnitOfMeasure.UNIT, true);
        when(catalog.findById(secondItem)).thenReturn(Optional.of(secondDetails));
        when(customers.findByIds(any())).thenReturn(List.of(
            new CustomerSnapshot(firstCustomer, "First customer", null, null, true),
            new CustomerSnapshot(secondCustomer, "Second customer", null, null, false)));

        var result = orders.search(new OrderSearchQuery(null, null, null, null, 0, 20));

        assertThat(result.content()).extracting(OrderResult::customerName).containsExactly("First customer", "Second customer");
        assertThat(result.content()).extracting(order -> order.lines().getFirst().itemName()).containsExactly("Product", "Second");
        verify(catalog, times(1)).findByIds(any());
        verify(customers, times(1)).findByIds(any());
        verify(customers, never()).findById(any());
    }

    @Test void createsWithoutCustomerUsingClockAndZeroPrice() {
        var result = save("0.000001", "0");
        assertThat(result.customerId()).isNull(); assertThat(result.customerName()).isNull();
        assertThat(result.currency()).isEqualTo("BRL"); assertThat(result.status()).isEqualTo(OrderStatus.DRAFT);
        assertThat(result.createdAt()).isEqualTo(NOW); assertThat(result.updatedAt()).isEqualTo(NOW);
        assertThat(result.total()).isEqualByComparingTo("0.00");
        verifyNoInteractions(customers);
    }
    @Test void roundsEachLineHalfUpBeforeSummingAndKeepsExactEnteredValues() {
        var second = UUID.randomUUID();
        when(catalog.findById(second)).thenReturn(Optional.of(new InventoryItemDetails(second, "Bulk", "FINISHED_PRODUCT", UnitOfMeasure.MILLILITER, true)));
        var result = orders.register(new SaveDraftCommand(null, List.of(line(item, "0.5", "0.01"), line(second, "0.5", "0.01"))));
        assertThat(result.lines()).extracting(OrderResult.Line::amount).containsExactly(new BigDecimal("0.01"), new BigDecimal("0.01"));
        assertThat(result.total()).isEqualTo(new BigDecimal("0.02"));
        assertThat(save("1234567890123.123456", "0.0001").lines().getFirst().quantity()).isEqualByComparingTo("1234567890123.123456");
        assertThat(save("0.000001", "999999999999999.9999").lines().getFirst().unitPrice()).isEqualByComparingTo("999999999999999.9999");
        assertThat(save("1", "1.2344").total()).isEqualByComparingTo("1.23");
        assertThat(save("1", "1.2350").total()).isEqualByComparingTo("1.24");
    }
    @ParameterizedTest @NullSource @ValueSource(strings = {"0", "-1", "0.0000001", "1.0000000", "10000000000000", "1E+100", "1E+2147483647"})
    void rejectsInvalidQuantityWithoutPersistence(String quantity) {
        assertThatThrownBy(() -> save(quantity, "1")).isInstanceOfSatisfying(OrderException.class,
            error -> assertThat(error.getDetails()).containsKey("lines[0].quantity"));
        verify(repository, never()).save(any());
    }
    @ParameterizedTest @NullSource @ValueSource(strings = {"-0.01", "0.00001", "1.00000", "1000000000000000", "1E+100"})
    void rejectsInvalidPriceWithoutPersistence(String price) {
        assertThatThrownBy(() -> save("1", price)).isInstanceOfSatisfying(OrderException.class,
            error -> assertThat(error.getDetails()).containsKey("lines[0].unitPrice"));
        verify(repository, never()).save(any());
    }
    @Test void rejectsLineAndAggregateOverflowAfterRounding() {
        assertThatThrownBy(() -> save("1000000000000", "1000000")).isInstanceOfSatisfying(OrderException.class,
            error -> assertThat(error.getDetails()).containsKey("lines[0].amount"));
        // Raw product is below the NUMERIC(19,2) maximum but HALF_UP rounding overflows it.
        assertThatThrownBy(() -> save("100.000037", "999999630000136.8999")).isInstanceOf(OrderException.class);
        var second = UUID.randomUUID();
        when(catalog.findById(second)).thenReturn(Optional.of(new InventoryItemDetails(second, "Second", "FINISHED_PRODUCT", UnitOfMeasure.UNIT, true)));
        assertThatThrownBy(() -> orders.register(new SaveDraftCommand(null, List.of(line(item, "100", "600000000000000"), line(second, "100", "600000000000000")))))
            .isInstanceOfSatisfying(OrderException.class, error -> assertThat(error.getDetails()).containsKey("total"));
        verify(repository, never()).save(any());
    }
    @Test void validatesOptionalCustomerThroughPublicLookupForEverySave() {
        var id = UUID.randomUUID();
        when(customers.findById(id)).thenReturn(Optional.of(new CustomerSnapshot(id, "Ana", null, null, true)));
        assertThat(orders.register(new SaveDraftCommand(id, List.of(line(item, "1", "1")))).customerId()).isEqualTo(id);
        when(customers.findById(id)).thenReturn(Optional.of(new CustomerSnapshot(id, "Ana", null, null, false)));
        assertThatThrownBy(() -> orders.register(new SaveDraftCommand(id, List.of(line(item, "1", "1")))))
            .isInstanceOfSatisfying(OrderException.class, e -> assertThat(e.getCode()).isEqualTo("ORDER_CUSTOMER_INACTIVE"));
        when(customers.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> orders.register(new SaveDraftCommand(id, List.of(line(item, "1", "1")))))
            .isInstanceOfSatisfying(OrderException.class, e -> assertThat(e.getCode()).isEqualTo("ORDER_CUSTOMER_NOT_FOUND"));
    }
    @ParameterizedTest @CsvSource({"true,ESSENCE,UNIT", "true,FINISHED_PRODUCT,LITER", "true,FINISHED_PRODUCT,GRAM", "true,FINISHED_PRODUCT,KILOGRAM", "false,FINISHED_PRODUCT,UNIT"})
    void rejectsInactiveOrIneligibleCatalogProducts(boolean active, String category, UnitOfMeasure unit) {
        when(catalog.findById(item)).thenReturn(Optional.of(new InventoryItemDetails(item, "Product", category, unit, active)));
        assertThatThrownBy(() -> save("1", "1")).isInstanceOfSatisfying(OrderException.class, e -> assertThat(e.getCode()).isEqualTo("ORDER_ITEM_INELIGIBLE"));
        verify(repository, never()).save(any());
    }
    @Test void rejectsMissingItemsDuplicateItemsEmptyAndNullLines() {
        when(catalog.findById(item)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> save("1", "1")).isInstanceOfSatisfying(OrderException.class, e -> assertThat(e.getCode()).isEqualTo("ORDER_ITEM_NOT_FOUND"));
        setUp();
        assertThatThrownBy(() -> orders.register(new SaveDraftCommand(null, List.of(line(item, "1", "1"), line(item, "2", "1"))))).isInstanceOf(OrderException.class);
        for (var lines : Arrays.asList(null, List.<SaveDraftCommand.Line>of(), Arrays.asList((SaveDraftCommand.Line) null), List.of(line(null, "1", "1")))) {
            assertThatThrownBy(() -> orders.register(new SaveDraftCommand(null, lines))).isInstanceOf(OrderException.class);
        }
        verify(repository, never()).save(any());
    }
    @Test void editsPreserveOrderAndRetainedItemLineIdentitiesAndRejectForeignOrReassignedIds() {
        var first = save("1", "1");
        var old = new Order(first.id(), null, OrderStatus.DRAFT,
            List.of(new OrderLine(first.lines().getFirst().id(), item, new BigDecimal("1"), new BigDecimal("1"), new BigDecimal("1.00"))),
            first.total(), NOW.minusSeconds(60), NOW.minusSeconds(30));
        when(repository.findByIdForUpdate(old.id())).thenReturn(Optional.of(old));
        var edited = orders.update(old.id(), new SaveDraftCommand(null, List.of(line(item, "2", "3"))));
        assertThat(edited.id()).isEqualTo(old.id()); assertThat(edited.createdAt()).isEqualTo(old.createdAt());
        assertThat(edited.updatedAt()).isEqualTo(NOW); assertThat(edited.lines().getFirst().id()).isEqualTo(old.lines().getFirst().id());
        assertThat(edited.total()).isEqualByComparingTo("6");
        var explicit = new SaveDraftCommand.Line(old.lines().getFirst().id(), item, BigDecimal.ONE, BigDecimal.ZERO);
        assertThat(orders.update(old.id(), new SaveDraftCommand(null, List.of(explicit))).lines().getFirst().id()).isEqualTo(explicit.id());
        var foreign = new SaveDraftCommand.Line(UUID.randomUUID(), item, BigDecimal.ONE, BigDecimal.ONE);
        assertThatThrownBy(() -> orders.update(old.id(), new SaveDraftCommand(null, List.of(foreign)))).isInstanceOf(OrderException.class);
        assertThatThrownBy(() -> orders.register(new SaveDraftCommand(null, List.of(explicit)))).isInstanceOf(OrderException.class);
        when(repository.findByIdForUpdate(old.id())).thenReturn(Optional.of(new Order(old.id(), null, OrderStatus.CONFIRMED, old.lines(), old.total(), old.createdAt(), NOW)));
        assertThatThrownBy(() -> orders.update(old.id(), new SaveDraftCommand(null, List.of(explicit))))
            .isInstanceOfSatisfying(OrderException.class, e -> assertThat(e.getCode()).isEqualTo("ORDER_NOT_EDITABLE"));
    }
    @Test void queryValidationUsesExistingPaginationAndDateLimits() {
        assertThat(new OrderSearchQuery(" ", null, null, null, 0, 20).q()).isNull();
        assertThat(new OrderSearchQuery(" ABC ", null, null, null, 0, 100).q()).isEqualTo("ABC");
        assertThatThrownBy(() -> new OrderSearchQuery(null, null, null, null, -1, 20)).isInstanceOf(OrderException.class);
        assertThatThrownBy(() -> new OrderSearchQuery(null, null, null, null, Integer.MAX_VALUE, 100)).isInstanceOf(OrderException.class);
        for (int size : new int[]{0, -1, 101}) assertThatThrownBy(() -> new OrderSearchQuery(null, null, null, null, 0, size)).isInstanceOf(OrderException.class);
        var today = LocalDate.ofInstant(NOW, ZoneOffset.UTC);
        assertThatThrownBy(() -> new OrderSearchQuery(null, null, today.plusDays(1), today, 0, 20)).isInstanceOf(OrderException.class);
        var missing = UUID.randomUUID();
        assertThatThrownBy(() -> orders.getById(missing)).isInstanceOfSatisfying(OrderException.class, e -> assertThat(e.getCode()).isEqualTo("ORDER_NOT_FOUND"));
        assertThatThrownBy(() -> orders.update(missing, new SaveDraftCommand(null, null))).isInstanceOf(OrderException.class);
    }
}
