package com.ceudelavanda.lavandaflow.sales;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import com.ceudelavanda.lavandaflow.sales.application.*;
import com.ceudelavanda.lavandaflow.customers.application.CustomerManagement;
import com.ceudelavanda.lavandaflow.customers.domain.CustomerContact;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "springdoc.api-docs.enabled=true")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@Transactional
@WithMockUser(authorities = "OPERATOR")
class OrderIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired OrderManagement orders;
    @Autowired CustomerManagement customers;
    @Autowired jakarta.persistence.EntityManager em;
    private UUID item;

    @BeforeEach void setUp() { item = insertItem("Finished", "FINISHED_PRODUCT", "UNIT", true); }
    private UUID insertItem(String name, String category, String unit, boolean active) {
        var id = UUID.randomUUID();
        jdbc.update("insert into inventory_item (id, name, category, default_unit, active) values (?, ?, ?, ?, ?)", id, name, category, unit, active);
        return id;
    }
    private SaveDraftCommand command(UUID customerId, UUID itemId, String quantity, String price) {
        return new SaveDraftCommand(customerId, List.of(new SaveDraftCommand.Line(null, itemId, new BigDecimal(quantity), new BigDecimal(price))));
    }
    private String body(UUID customer, UUID item, String quantity, String price) {
        return "{\"customerId\":" + (customer == null ? "null" : "\"" + customer + "\"") + ",\"lines\":[{\"itemId\":\"" + item + "\",\"quantity\":\"" + quantity + "\",\"unitPrice\":\"" + price + "\"}]}";
    }

    @Test void httpCreateDetailListAndReplacementPreserveIdentityAndExactDecimalsWithoutStockEffects() throws Exception {
        var batch = UUID.randomUUID();
        jdbc.update("insert into inventory_batch (id, inventory_item_id, initial_quantity, current_quantity, received_at) values (?, ?, 5, 5, current_date)", batch, item);
        jdbc.update("insert into stock_movement (id, batch_id, movement_type, quantity, occurred_at) values (?, ?, 'ENTRY', 5, current_timestamp)", UUID.randomUUID(), batch);
        var stock = jdbc.queryForList("select * from inventory_batch order by id");
        var movements = jdbc.queryForList("select * from stock_movement order by id");
        var create = mvc.perform(post("/api/v1/sales").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(null, item, "0.5", "0.01")))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.customerId").isEmpty()).andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.currency").value("BRL")).andExpect(jsonPath("$.lines[0].quantity").value("0.500000"))
            .andExpect(jsonPath("$.lines[0].unitPrice").value("0.0100")).andExpect(jsonPath("$.lines[0].amount").value("0.01"))
            .andExpect(jsonPath("$.total").value("0.01")).andReturn();
        var location = create.getResponse().getHeader("Location");
        var id = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
        var original = orders.getById(id); var lineId = original.lines().getFirst().id();
        mvc.perform(get(location)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toString()));
        var customer = customers.register(new CustomerContact("Ana", null, null));
        mvc.perform(put(location).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(customer.id(), item, "2.000001", "1.2345")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toString())).andExpect(jsonPath("$.customerName").value("Ana"))
            .andExpect(jsonPath("$.lines[0].id").value(lineId.toString())).andExpect(jsonPath("$.total").value("2.47"));
        em.clear();
        var stored = orders.getById(id);
        assertThat(stored.createdAt()).isEqualTo(original.createdAt());
        assertThat(stored.lines().getFirst().quantity()).isEqualByComparingTo("2.000001");
        assertThat(jdbc.queryForObject("select count(*) from sales_order_line where order_id = ?", Long.class, id)).isEqualTo(1);
        mvc.perform(get("/api/v1/sales").param("q", id.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20));
        mvc.perform(put(location).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(null, item, "0", "1")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.details['lines[0].quantity']").exists());
        assertThat(orders.getById(id)).isEqualTo(stored);
        mvc.perform(put(location).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(null, item, "9999999999999.999999", "0")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.customerId").isEmpty()).andExpect(jsonPath("$.total").value("0.00"));
        assertThat(jdbc.queryForList("select * from inventory_batch order by id")).isEqualTo(stock);
        assertThat(jdbc.queryForList("select * from stock_movement order by id")).isEqualTo(movements);
    }

    @Test void replacingAddingRemovingAndReorderingLinesPreservesRetainedUuidAndEnteredValues() {
        var bulk = insertItem("Bulk", "FINISHED_PRODUCT", "MILLILITER", true);
        var first = orders.register(command(null, item, "1", "2"));
        var lineId = first.lines().getFirst().id();
        var updated = orders.update(first.id(), new SaveDraftCommand(null, List.of(
            new SaveDraftCommand.Line(null, bulk, new BigDecimal("0.500001"), new BigDecimal("0.0123")),
            new SaveDraftCommand.Line(lineId, item, new BigDecimal("3"), new BigDecimal("4")))));
        assertThat(updated.lines()).extracting(OrderResult.Line::itemId).containsExactly(bulk, item);
        assertThat(updated.lines().get(1).id()).isEqualTo(lineId);
        var retainedBulkId = updated.lines().getFirst().id();
        orders.update(first.id(), command(null, bulk, "1", "0")); em.clear();
        assertThat(orders.getById(first.id()).lines()).extracting(OrderResult.Line::id).containsExactly(retainedBulkId);
        assertThat(jdbc.queryForObject("select count(*) from sales_order_line where order_id = ?", Long.class, first.id())).isEqualTo(1);
    }

    @Test void acceptsOmittedCustomerAndLegacyJsonNumbersWhileReturningExactDecimalStrings() throws Exception {
        mvc.perform(post("/api/v1/sales").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"lines\":[{\"itemId\":\"" + item + "\",\"quantity\":1.000001,\"unitPrice\":1.2350}]}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.customerId").isEmpty())
            .andExpect(jsonPath("$.lines[0].quantity").value("1.000001"))
            .andExpect(jsonPath("$.lines[0].unitPrice").value("1.2350"))
            .andExpect(jsonPath("$.total").value("1.24"));
    }

    @Test void currentLabelsMayChangeButDraftIdentitiesQuantitiesPricesAndTotalsDoNot() {
        var customer = customers.register(new CustomerContact("Before", null, null));
        var saved = orders.register(command(customer.id(), item, "1.123456", "2.1234"));
        customers.update(customer.id(), new CustomerContact("After", null, null));
        customers.changeActiveState(customer.id(), false);
        em.flush();
        jdbc.update("update inventory_item set name = 'Renamed', active = false where id = ?", item); em.clear();
        var read = orders.getById(saved.id());
        assertThat(read.customerName()).isEqualTo("After"); assertThat(read.lines().getFirst().itemName()).isEqualTo("Renamed");
        assertThat(read.lines().getFirst().id()).isEqualTo(saved.lines().getFirst().id());
        assertThat(read.lines().getFirst().quantity()).isEqualByComparingTo(saved.lines().getFirst().quantity());
        assertThat(read.lines().getFirst().unitPrice()).isEqualByComparingTo(saved.lines().getFirst().unitPrice());
        assertThat(read.total()).isEqualByComparingTo(saved.total());
        assertThatThrownBy(() -> orders.update(saved.id(), command(customer.id(), item, "2", "1")))
            .isInstanceOfSatisfying(OrderException.class, e -> assertThat(e.getCode()).isEqualTo("ORDER_CUSTOMER_INACTIVE"));
        assertThatThrownBy(() -> orders.update(saved.id(), command(null, item, "2", "1")))
            .isInstanceOfSatisfying(OrderException.class, e -> assertThat(e.getCode()).isEqualTo("ORDER_ITEM_INELIGIBLE"));
    }

    @Test void livePublicLookupsRejectMissingInactiveAndIneligibleReferencesWithStandardErrors() throws Exception {
        var customer = customers.register(new CustomerContact("Active", null, null));
        customers.changeActiveState(customer.id(), false);
        for (var entry : Map.of(customer.id(), "ORDER_CUSTOMER_INACTIVE", UUID.randomUUID(), "ORDER_CUSTOMER_NOT_FOUND").entrySet()) {
            mvc.perform(post("/api/v1/sales").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(entry.getKey(), item, "1", "1")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(entry.getValue()));
        }
        var inactive = insertItem("Inactive", "FINISHED_PRODUCT", "UNIT", false);
        var category = insertItem("Input", "OTHER", "UNIT", true);
        var unit = insertItem("Liters", "FINISHED_PRODUCT", "LITER", true);
        for (var id : List.of(inactive, category, unit, UUID.randomUUID())) {
            mvc.perform(post("/api/v1/sales").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(null, id, "1", "1")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(id.equals(inactive) || id.equals(category) || id.equals(unit) ? "ORDER_ITEM_INELIGIBLE" : "ORDER_ITEM_NOT_FOUND"));
        }
        assertThat(jdbc.queryForObject("select count(*) from sales_order", Long.class)).isZero();
    }

    @Test void pagesDeterministicallyFiltersCustomerAndInclusiveUtcDatesAndEscapesSearch() throws Exception {
        var customer = customers.register(new CustomerContact("Filter", null, null));
        var first = orders.register(command(customer.id(), item, "1", "1"));
        var second = orders.register(command(customer.id(), item, "1", "2"));
        var timestamp = Instant.parse("2026-10-06T23:59:59Z");
        jdbc.update("update sales_order set created_at = ? where customer_id = ?", java.sql.Timestamp.from(timestamp), customer.id()); em.clear();
        var today = LocalDate.of(2026, 10, 6);
        var sorted = List.of(first.id(), second.id()).stream().sorted(Comparator.comparing(UUID::toString)).toList();
        assertThat(orders.search(new OrderSearchQuery(null, customer.id(), today, today, 0, 1)).content()).extracting(OrderResult::id).containsExactly(sorted.getFirst());
        assertThat(orders.search(new OrderSearchQuery(null, customer.id(), today, today, 1, 1)).content()).extracting(OrderResult::id).containsExactly(sorted.getLast());
        assertThat(orders.search(new OrderSearchQuery(null, customer.id(), today.plusDays(1), null, 0, 20)).content()).isEmpty();
        assertThat(orders.search(new OrderSearchQuery(null, UUID.randomUUID(), null, null, 0, 20)).content()).isEmpty();
        assertThat(orders.search(new OrderSearchQuery("%_!", null, null, null, 0, 20)).content()).isEmpty();
        mvc.perform(get("/api/v1/sales").param("q", first.id().toString().toUpperCase()).param("size", "100"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        jdbc.update("update sales_order set status = 'CANCELLED' where id = ?", first.id()); em.clear();
        assertThat(orders.search(new OrderSearchQuery(null, customer.id(), null, null, 0, 20)).totalElements()).isEqualTo(1);
        mvc.perform(get("/api/v1/sales/" + first.id())).andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/sales/" + first.id()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(null,item,"1","1")))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_NOT_EDITABLE"));
    }

    @Test void searchesFullPageWithConstantQueryCountAndEagerlyLoadsLines() {
        var customer = customers.register(new CustomerContact("Batch lookup", null, null));
        for (int i = 0; i < 20; i++) orders.register(command(customer.id(), item, "1", "1"));
        em.clear();
        var statistics = em.getEntityManagerFactory().unwrap(org.hibernate.SessionFactory.class).getStatistics();
        var wasEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        try {
            var page = orders.search(new OrderSearchQuery(null, null, null, null, 0, 20));

            assertThat(page.content()).hasSize(20).allSatisfy(order -> {
                assertThat(order.customerName()).isEqualTo("Batch lookup");
                assertThat(order.lines()).hasSize(1);
                assertThat(order.lines().getFirst().itemName()).isEqualTo("Finished");
            });
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(5);
        } finally {
            statistics.clear();
            statistics.setStatisticsEnabled(wasEnabled);
        }
    }

    @Test void malformedInvalidAndMissingOrdersUseExistingErrorShapeAndNeverWrite() throws Exception {
        for (var invalid : new String[]{"{}", "{\"lines\":[]}", "{\"lines\":[null]}", body(null,item,"0.0000001","1"), body(null,item,"1","1.00001"), body(null,item,"100.000037","999999630000136.8999")}) {
            mvc.perform(post("/api/v1/sales").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        mvc.perform(post("/api/v1/sales").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST_BODY"));
        mvc.perform(get("/api/v1/sales/" + UUID.randomUUID())).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        for (var param : new String[]{"page", "size", "from", "to", "customerId"}) {
            mvc.perform(get("/api/v1/sales").param(param, "invalid")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST_PARAMETER"));
        }
        mvc.perform(get("/api/v1/sales").param("page", "2147483647").param("size", "100"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_ORDER_SEARCH_QUERY"));
        for (var entry : Map.of("page", "-1", "size", "101").entrySet()) {
            mvc.perform(get("/api/v1/sales").param(entry.getKey(), entry.getValue())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_ORDER_SEARCH_QUERY"));
        }
    }

    @Test void requiresSessionOperatorAndCsrfForEveryWriteAndDocumentsOnlyDraftContracts() throws Exception {
        var detail = "/api/v1/sales/" + UUID.randomUUID();
        for (var url : new String[]{"/api/v1/sales", detail}) {
            mvc.perform(get(url).with(anonymous())).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
            mvc.perform(get(url).with(user("viewer").authorities(() -> "VIEWER"))).andExpect(status().isForbidden());
        }
        for (var request : List.of(post("/api/v1/sales"), put(detail))) {
            mvc.perform(request.with(anonymous()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(null,item,"1","1"))).andExpect(status().isUnauthorized());
            mvc.perform(request.with(user("viewer").authorities(() -> "VIEWER")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(null,item,"1","1")))
                .andExpect(status().isForbidden());
            mvc.perform(request.with(user("operator").authorities(() -> "OPERATOR")).with(csrf().useInvalidToken()).contentType(MediaType.APPLICATION_JSON).content(body(null,item,"1","1")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_VALIDATION_FAILED"));
        }
        mvc.perform(post("/api/v1/sales").contentType(MediaType.APPLICATION_JSON).content(body(null,item,"1","1"))).andExpect(status().isForbidden());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/sales'].post.responses['201']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/sales'].get").exists())
            .andExpect(jsonPath("$.paths['/api/v1/sales/{orderId}'].get").exists())
            .andExpect(jsonPath("$.paths['/api/v1/sales/{orderId}'].put").exists())
            .andExpect(jsonPath("$.paths['/api/v1/sales/{orderId}/confirm']").doesNotExist())
            .andExpect(jsonPath("$.paths['/api/v1/sales/{orderId}'].delete").doesNotExist())
            .andExpect(jsonPath("$.components.schemas.SaveDraftRequest.required", org.hamcrest.Matchers.contains("lines")))
            .andExpect(jsonPath("$.components.schemas.SaveDraftRequest.properties.lines.minItems").value(1))
            .andExpect(jsonPath("$.components.schemas.SaveDraftRequest.properties.lines.items['$ref']").value("#/components/schemas/DraftOrderLineRequest"))
            .andExpect(jsonPath("$.components.schemas.DraftOrderLineRequest.required", org.hamcrest.Matchers.containsInAnyOrder("itemId", "quantity", "unitPrice")))
            .andExpect(jsonPath("$.components.schemas.DraftOrderLineRequest.properties.quantity.type").value("string"))
            .andExpect(jsonPath("$.components.schemas.DraftOrderLineRequest.properties.unitPrice.type").value("string"))
            .andExpect(jsonPath("$.components.schemas.DraftOrderLineRequest.properties.amount").doesNotExist())
            .andExpect(jsonPath("$.components.schemas.Line.properties.amount.type").value("string"))
            .andExpect(jsonPath("$.components.schemas.OrderResult.properties.total.type").value("string"));
    }

    @ParameterizedTest @ValueSource(strings = {"quantity = 0", "quantity = -1", "quantity = 10000000000000", "unit_price = -1", "unit_price = 1000000000000000", "amount = -1", "amount = 100000000000000000", "amount = 99", "position = -1", "order_id = '00000000-0000-0000-0000-000000000000'", "item_id = NULL"})
    void postgresDefendsLineConstraints(String assignment) {
        var saved = orders.register(command(null,item,"1","1"));
        assertThatThrownBy(() -> jdbc.update("update sales_order_line set " + assignment + " where order_id = ?", saved.id())).isInstanceOf(DataIntegrityViolationException.class);
    }
    @ParameterizedTest @ValueSource(strings = {"status = 'UNKNOWN'", "total = -1", "total = 100000000000000000", "created_at = NULL", "updated_at = NULL", "customer_id = '00000000-0000-0000-0000-000000000000'"})
    void postgresDefendsAggregateConstraints(String assignment) {
        var saved = orders.register(command(null,item,"1","1"));
        assertThatThrownBy(() -> jdbc.update("update sales_order set " + assignment + " where id = ?", saved.id())).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void postgresRejectsDuplicateItemsInOneOrder() {
        var saved = orders.register(command(null,item,"1","1"));
        assertThatThrownBy(() -> jdbc.update("insert into sales_order_line (id, order_id, item_id, position, quantity, unit_price, amount) values (?, ?, ?, 1, 1, 1, 1)", UUID.randomUUID(), saved.id(), item))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
