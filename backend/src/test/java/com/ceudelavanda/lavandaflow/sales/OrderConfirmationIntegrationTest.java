package com.ceudelavanda.lavandaflow.sales;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import com.ceudelavanda.lavandaflow.inventory.SaleStockWithdrawal;
import com.ceudelavanda.lavandaflow.sales.application.*;
import com.ceudelavanda.lavandaflow.sales.domain.OrderStatus;
import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.customers.application.CustomerManagement;
import com.ceudelavanda.lavandaflow.customers.domain.CustomerContact;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real, separately committed PostgreSQL transactions; never uses the operational database. */
@SpringBootTest(properties = "springdoc.api-docs.enabled=true")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@WithMockUser(authorities = "OPERATOR")
class OrderConfirmationIntegrationTest {
    @Autowired OrderManagement orders;
    @Autowired CustomerManagement customers;
    @Autowired SaleStockWithdrawal inventory;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired Clock clock;
    @Autowired MockMvc mvc;
    private UUID item;

    @BeforeEach void setUp() { item = item(UUID.randomUUID(), "UNIT"); }

    private UUID item(UUID id, String unit) {
        jdbc.update("insert into inventory_item (id, name, category, default_unit, active) values (?, 'Product', 'FINISHED_PRODUCT', ?, true)", id, unit);
        return id;
    }
    private UUID batch(UUID product, String quantity, Integer days) {
        var id = UUID.randomUUID(); var today = LocalDate.now(clock);
        jdbc.update("insert into inventory_batch (id, inventory_item_id, initial_quantity, current_quantity, received_at, expires_at) values (?, ?, ?, ?, ?, ?)",
            id, product, new BigDecimal(quantity), new BigDecimal(quantity), today.minusDays(1), days == null ? null : today.plusDays(days));
        return id;
    }
    private SaveDraftCommand.Line line(UUID product, String quantity) {
        return new SaveDraftCommand.Line(null, product, new BigDecimal(quantity), new BigDecimal("1.2345"));
    }
    private OrderResult draft(String quantity) { return orders.register(new SaveDraftCommand(null, List.of(line(item, quantity)))); }
    private BigDecimal balance(UUID batch) { return jdbc.queryForObject("select current_quantity from inventory_batch where id = ?", BigDecimal.class, batch); }
    private long movements(UUID order) { return jdbc.queryForObject("select count(*) from stock_movement where reference_id = ?", Long.class, order); }
    private long allocations(UUID order) { return jdbc.queryForObject("select count(*) from sale_allocation a join sales_order_line l on l.id = a.line_id where l.order_id = ?", Long.class, order); }
    private void unchanged(OrderResult draft) {
        assertThat(orders.getById(draft.id()).status()).isEqualTo(OrderStatus.DRAFT);
        assertThat(orders.getById(draft.id()).confirmedAt()).isNull();
        assertThat(jdbc.queryForObject("select count(*) from sales_order_line where order_id = ? and item_name is not null", Long.class, draft.id())).isZero();
        assertThat(movements(draft.id())).isZero(); assertThat(allocations(draft.id())).isZero();
    }

    @Test void confirmsExactMultiBatchFefoAndPersistsAuditAndImmutableSnapshots() throws Exception {
        var customer = customers.register(new CustomerContact("Original", "11999998888", "original@example.com"));
        var early = batch(item, "1.000001", 1); var later = batch(item, "5", 2);
        var expired = batch(item, "100", 0); var undated = batch(item, "100", null);
        var draft = orders.register(new SaveDraftCommand(customer.id(), List.of(line(item, "3.000002"))));
        var result = orders.confirm(draft.id());
        assertThat(result.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(result.confirmedAt()).isNotNull();
        assertThat(result.total()).isEqualByComparingTo(draft.total());
        assertThat(result.lines().getFirst().allocations()).extracting(a -> a.batchId()).containsExactly(early, later);
        assertThat(result.lines().getFirst().allocations()).extracting(a -> a.quantity()).usingElementComparator(BigDecimal::compareTo)
            .containsExactly(new BigDecimal("1.000001"), new BigDecimal("2.000001"));
        assertThat(balance(early)).isZero(); assertThat(balance(later)).isEqualByComparingTo("2.999999");
        assertThat(balance(expired)).isEqualByComparingTo("100"); assertThat(balance(undated)).isEqualByComparingTo("100");
        for (var a : result.lines().getFirst().allocations()) {
            var movement = jdbc.queryForMap("select * from stock_movement where id = ?", a.movementId());
            assertThat(movement).containsEntry("reference_type", "SALE").containsEntry("reference_id", draft.id())
                .containsEntry("reference_line_id", draft.lines().getFirst().id()).containsEntry("batch_id", a.batchId());
            assertThat((BigDecimal) movement.get("quantity")).isEqualByComparingTo(a.quantity());
            assertThat(jdbc.queryForObject("select movement_id from sale_allocation where line_id = ? and batch_id = ?", UUID.class, draft.lines().getFirst().id(), a.batchId())).isEqualTo(a.movementId());
        }
        customers.update(customer.id(), new CustomerContact("Changed", "11888887777", "changed@example.com"));
        customers.changeActiveState(customer.id(), false);
        jdbc.update("update inventory_item set name = 'Changed', active = false where id = ?", item);
        assertThat(orders.getById(draft.id())).isEqualTo(result);
        assertThat(orders.confirm(draft.id())).isEqualTo(result);
        assertThat(movements(draft.id())).isEqualTo(2); assertThat(allocations(draft.id())).isEqualTo(2);
        mvc.perform(get("/api/v1/sales/" + draft.id())).andExpect(status().isOk()).andExpect(jsonPath("$.customerName").value("Original"));
        mvc.perform(post("/api/v1/sales/" + draft.id() + "/confirm").with(csrf())).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CONFIRMED")).andExpect(jsonPath("$.lines[0].allocations[0].batchId").value(early.toString()));
        assertThatThrownBy(() -> orders.cancel(draft.id())).isInstanceOf(OrderException.class);
        assertThatThrownBy(() -> orders.update(draft.id(), new SaveDraftCommand(null, List.of(line(item, "1"))))).isInstanceOf(OrderException.class);
        mvc.perform(delete("/api/v1/sales/" + draft.id()).with(csrf())).andExpect(status().isMethodNotAllowed());
    }

    @Test void insufficientSecondItemRollsBackEarlierWithdrawalAndAllowsExplicitRetry() {
        var second = item(new UUID(Long.MAX_VALUE, Long.MAX_VALUE), "MILLILITER");
        // Guarantee processing order independently of randomly generated UUIDs.
        var first = item(new UUID(Long.MIN_VALUE, Long.MIN_VALUE), "UNIT");
        var stock = batch(first, "5", 1); var expired = batch(second, "100", 0);
        var draft = orders.register(new SaveDraftCommand(null, List.of(line(second, "2"), line(first, "2"))));
        assertThatThrownBy(() -> orders.confirm(draft.id())).isInstanceOfSatisfying(DomainException.class,
            e -> assertThat(e.getCode()).isEqualTo("INSUFFICIENT_ELIGIBLE_STOCK"));
        unchanged(draft); assertThat(balance(stock)).isEqualByComparingTo("5"); assertThat(balance(expired)).isEqualByComparingTo("100");
        batch(second, "2", 1);
        assertThat(orders.confirm(draft.id()).status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(balance(stock)).isEqualByComparingTo("3");
    }

    @Test void persistenceFailureAfterInventoryWritesRollsBackAllEffectsAndRetryWorks() throws Exception {
        var stock = batch(item, "5", 1); var draft = draft("2");
        jdbc.execute("create function issue262_fail_allocation() returns trigger language plpgsql as $$ begin raise exception 'injected allocation persistence failure'; end $$");
        jdbc.execute("create trigger issue262_fail before insert on sale_allocation for each row execute function issue262_fail_allocation()");
        try {
            assertThatThrownBy(() -> orders.confirm(draft.id())).isInstanceOf(RuntimeException.class);
            mvc.perform(post("/api/v1/sales/" + draft.id() + "/confirm").with(csrf()))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("ORDER_PERSISTENCE_FAILED"))
                .andExpect(jsonPath("$.message").value("Order operation could not be persisted"))
                .andExpect(jsonPath("$.details").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("injected allocation persistence failure"))));
            unchanged(draft); assertThat(balance(stock)).isEqualByComparingTo("5");
        } finally {
            jdbc.execute("drop trigger issue262_fail on sale_allocation"); jdbc.execute("drop function issue262_fail_allocation()");
        }
        assertThat(orders.confirm(draft.id()).status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(movements(draft.id())).isEqualTo(1); assertThat(balance(stock)).isEqualByComparingTo("3");
    }

    @Test void concurrentConfirmationSerializesOnOrderRowAndCannotWithdrawTwice() throws Exception {
        var customer = customers.register(new CustomerContact("Concurrent customer", "11999998888", "concurrent@example.com"));
        var stock = batch(item, "5", 1);
        var draft = orders.register(new SaveDraftCommand(customer.id(), List.of(line(item, "2"))));
        OrderResult confirmed;
        var held = new CountDownLatch(1); var release = new CountDownLatch(1); var started = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> new TransactionTemplate(transactions).execute(tx -> {
                var result = orders.confirm(draft.id()); held.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test release timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                return result;
            }));
            try {
                assertThat(held.await(5, TimeUnit.SECONDS)).isTrue();
                var second = executor.submit(() -> { started.countDown(); return orders.confirm(draft.id()); });
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> second.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                release.countDown();
                confirmed = first.get(5, TimeUnit.SECONDS);
                assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo(confirmed);
            } finally { release.countDown(); }
        }
        var persisted = orders.getById(draft.id());
        assertThat(persisted).isEqualTo(confirmed);
        assertThat(persisted.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(persisted.confirmedAt()).isNotNull();
        assertThat(persisted.customerName()).isEqualTo(customer.name());
        assertThat(persisted.customerPhone()).isEqualTo(customer.phone());
        assertThat(persisted.customerEmail()).isEqualTo(customer.email());
        var persistedLine = persisted.lines().getFirst();
        assertThat(persistedLine.itemName()).isEqualTo("Product");
        assertThat(persistedLine.unitOfMeasure()).isEqualTo(com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure.UNIT);
        assertThat(persistedLine.allocations()).hasSize(1);
        assertThat(persistedLine.allocations().getFirst().batchId()).isEqualTo(stock);
        assertThat(persistedLine.allocations().getFirst().quantity()).isEqualByComparingTo("2");
        assertThat(balance(stock)).isEqualByComparingTo("3"); assertThat(movements(draft.id())).isEqualTo(1); assertThat(allocations(draft.id())).isEqualTo(1);
    }

    @Test void cancellationHasNoStockEffectAndCancelledCannotConfirm() {
        var stock = batch(item, "5", 1); var draft = draft("2");
        assertThat(orders.cancel(draft.id()).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThatThrownBy(() -> orders.confirm(draft.id())).isInstanceOfSatisfying(OrderException.class,
            e -> assertThat(e.getCode()).isEqualTo("ORDER_NOT_CONFIRMABLE"));
        assertThat(balance(stock)).isEqualByComparingTo("5"); assertThat(movements(draft.id())).isZero();
    }

    @Test void publicInventoryContractRequiresCallerTransaction() {
        assertThatThrownBy(() -> inventory.withdraw(List.of())).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }

    @Test void databaseDefendsConfirmedHistoryAuditAndDuplicateConsumption() {
        var stock = batch(item, "5", 1); var result = orders.confirm(draft("2").id());
        var line = result.lines().getFirst(); var allocation = line.allocations().getFirst();
        for (var sql : List.of(
            "update sales_order set customer_name = 'Changed' where id = '" + result.id() + "'",
            "delete from sales_order where id = '" + result.id() + "'",
            "update sales_order_line set item_name = 'Changed' where id = '" + line.id() + "'",
            "delete from sales_order_line where id = '" + line.id() + "'",
            "update sale_allocation set quantity = 1 where line_id = '" + line.id() + "'",
            "delete from sale_allocation where line_id = '" + line.id() + "'",
            "update stock_movement set reason = 'Changed' where id = '" + allocation.movementId() + "'",
            "delete from stock_movement where id = '" + allocation.movementId() + "'")) {
            assertThatThrownBy(() -> jdbc.execute(sql)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        }
        assertThatThrownBy(() -> jdbc.update("insert into stock_movement (id, batch_id, movement_type, quantity, occurred_at, reference_type, reference_id, reference_line_id) values (?, ?, 'CONSUMPTION', 2, current_timestamp, 'SALE', ?, ?)",
            UUID.randomUUID(), stock, result.id(), line.id())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into sale_allocation (id, line_id, batch_id, movement_id, quantity, position) values (?, ?, ?, ?, 2, 0)",
            UUID.randomUUID(), line.id(), stock, allocation.movementId())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(orders.getById(result.id())).isEqualTo(result);
    }

    @Test void batchLockTimeoutAfterEarlierWithdrawalRollsBackWholeConfirmation() throws Exception {
        var ids = List.of(UUID.randomUUID(), UUID.randomUUID()).stream().sorted().toList();
        var first = item(ids.getFirst(), "UNIT"); var second = item(ids.getLast(), "UNIT");
        var firstStock = batch(first, "5", 1); var secondStock = batch(second, "5", 1);
        var draft = orders.register(new SaveDraftCommand(null, List.of(line(first, "2"), line(second, "2"))));
        var held = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var blocker = executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                jdbc.queryForObject("select id from inventory_batch where id = ? for update", UUID.class, secondStock);
                held.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test release timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }));
            try {
                assertThat(held.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                    jdbc.execute("set local lock_timeout = '200ms'"); orders.confirm(draft.id());
                })).isInstanceOf(org.springframework.dao.PessimisticLockingFailureException.class);
                unchanged(draft);
                assertThat(balance(firstStock)).isEqualByComparingTo("5"); assertThat(balance(secondStock)).isEqualByComparingTo("5");
            } finally { release.countDown(); }
            blocker.get(5, TimeUnit.SECONDS);
        }
        assertThat(orders.confirm(draft.id()).status()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test void changedCustomerAndProductEligibilityRejectConfirmationWithoutEffects() {
        var customer = customers.register(new CustomerContact("Customer", null, null));
        var draft = orders.register(new SaveDraftCommand(customer.id(), List.of(line(item, "2"))));
        var stock = batch(item, "5", 1);
        customers.changeActiveState(customer.id(), false);
        assertThatThrownBy(() -> orders.confirm(draft.id())).isInstanceOfSatisfying(OrderException.class,
            e -> assertThat(e.getCode()).isEqualTo("ORDER_CUSTOMER_INACTIVE"));
        unchanged(draft);
        customers.changeActiveState(customer.id(), true);
        jdbc.update("update inventory_item set active = false where id = ?", item);
        assertThatThrownBy(() -> orders.confirm(draft.id())).isInstanceOfSatisfying(DomainException.class,
            e -> assertThat(e.getCode()).isEqualTo("SALE_ITEM_INELIGIBLE"));
        unchanged(draft); assertThat(balance(stock)).isEqualByComparingTo("5");
    }

    @Test void httpOrderLockTimeoutUsesStandardConflictShapeAndLeavesDraft() throws Exception {
        var stock = batch(item, "5", 1); var draft = draft("2");
        var held = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var blocker = executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                jdbc.queryForObject("select id from sales_order where id = ? for update", UUID.class, draft.id());
                held.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test release timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }));
            try {
                assertThat(held.await(5, TimeUnit.SECONDS)).isTrue();
                new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                    jdbc.execute("set local lock_timeout = '200ms'");
                    try {
                        mvc.perform(post("/api/v1/sales/" + draft.id() + "/confirm").with(csrf()))
                            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_LOCK_CONFLICT"));
                    } catch (Exception e) { throw new IllegalStateException(e); }
                    tx.setRollbackOnly();
                });
            } finally { release.countDown(); }
            blocker.get(5, TimeUnit.SECONDS);
        }
        unchanged(draft); assertThat(balance(stock)).isEqualByComparingTo("5");
    }

    @Test void confirmationUsesExistingAuthenticationCsrfAndInsufficientStockErrors() throws Exception {
        var draft = draft("2"); var url = "/api/v1/sales/" + draft.id() + "/confirm";
        mvc.perform(post(url).with(anonymous()).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post(url).with(user("viewer").authorities(() -> "VIEWER")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post(url)).andExpect(status().isForbidden());
        mvc.perform(post(url).with(csrf())).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INSUFFICIENT_ELIGIBLE_STOCK"));
        unchanged(draft);
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/sales/{orderId}/confirm'].post.responses['422']").exists());
    }

    @Test void openApiDescribesPersistedConfirmationAndPrincipalErrors() throws Exception {
        var json = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var document = tools.jackson.databind.json.JsonMapper.builder().build().readTree(json);
        var responses = document.at("/paths/~1api~1v1~1sales~1{orderId}~1confirm/post/responses");
        assertThat(responses.isMissingNode()).isFalse();
        var result = responseSchema(document, responses.path("200"));
        for (var field : List.of("id", "status", "customerName", "customerPhone", "customerEmail", "confirmedAt", "lines"))
            assertThat(result.path("properties").has(field)).as("confirmation field %s", field).isTrue();
        var line = resolveSchema(document, result.at("/properties/lines/items"));
        for (var field : List.of("itemId", "itemName", "unitOfMeasure", "quantity", "unitPrice", "amount", "allocations"))
            assertThat(line.path("properties").has(field)).as("line field %s", field).isTrue();
        var allocation = resolveSchema(document, line.at("/properties/allocations/items"));
        for (var field : List.of("batchId", "movementId", "quantity"))
            assertThat(allocation.path("properties").has(field)).as("allocation field %s", field).isTrue();
        for (var status : List.of("400", "401", "403", "404", "409", "422", "500")) {
            var error = responseSchema(document, responses.path(status));
            for (var field : List.of("status", "code", "message", "path"))
                assertThat(error.path("properties").has(field)).as("error %s field %s", status, field).isTrue();
        }
    }

    private static tools.jackson.databind.JsonNode responseSchema(tools.jackson.databind.JsonNode document,
        tools.jackson.databind.JsonNode response) {
        assertThat(response.path("content").isEmpty()).as("response content schema").isFalse();
        return resolveSchema(document, response.path("content").iterator().next().path("schema"));
    }

    private static tools.jackson.databind.JsonNode resolveSchema(tools.jackson.databind.JsonNode document,
        tools.jackson.databind.JsonNode schema) {
        return schema.has("$ref") ? document.at(schema.path("$ref").asString().substring(1)) : schema;
    }
}
