package com.ceudelavanda.lavandaflow.sales;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import com.ceudelavanda.lavandaflow.inventory.SaleStockReturn;
import com.ceudelavanda.lavandaflow.sales.application.*;
import com.ceudelavanda.lavandaflow.sales.domain.SaleStockCorrection;
import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
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
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercises real committed PostgreSQL transactions, including concurrent requests and injected SQL failures. */
@SpringBootTest(properties = "springdoc.api-docs.enabled=true")
@Import({TestcontainersConfiguration.class, SaleStockCorrectionIntegrationTest.ReturnClock.class})
@AutoConfigureMockMvc
@WithMockUser(authorities = "OPERATOR")
class SaleStockCorrectionIntegrationTest {
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class ReturnClock {
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        Clock returnClock() {
            return Clock.fixed(Instant.parse("2026-10-19T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        }
    }
    @Autowired OrderManagement orders;
    @Autowired SaleStockCorrections corrections;
    @Autowired SaleStockReturn inventory;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired Clock clock;
    UUID item;
    UUID batch;
    OrderResult sale;

    @BeforeEach void setup() {
        item = UUID.randomUUID(); batch = UUID.randomUUID();
        jdbc.update("insert into inventory_item (id,name,category,default_unit,active) values (?, 'Original product', 'FINISHED_PRODUCT', 'MILLILITER', true)", item);
        var today = LocalDate.now(clock);
        jdbc.update("insert into inventory_batch (id,inventory_item_id,initial_quantity,current_quantity,received_at,expires_at) values (?, ?, 3.000001, 3.000001, ?, ?)", batch, item, today.minusDays(1), today.plusDays(2));
        sale = orders.confirm(orders.register(new SaveDraftCommand(null, List.of(
            new SaveDraftCommand.Line(null, item, new BigDecimal("3.000001"), new BigDecimal("2.1234"))))).id());
    }
    RegisterSaleStockCorrection request(String quantity) {
        return new RegisterSaleStockCorrection(sale.lines().getFirst().id(), batch, new BigDecimal(quantity), " Inspected return ", true, true);
    }
    SaleStockCorrection accept(UUID id, String quantity) { return corrections.register(sale.id(), id, request(quantity), "operator"); }
    BigDecimal balance() { return jdbc.queryForObject("select current_quantity from inventory_batch where id = ?", BigDecimal.class, batch); }
    long movementCount() { return jdbc.queryForObject("select count(*) from stock_movement where reference_id = ?", Long.class, sale.id()); }
    void noReturn() {
        assertThat(balance()).isZero(); assertThat(movementCount()).isEqualTo(1);
        assertThat(corrections.list(sale.id())).isEmpty(); assertThat(orders.getById(sale.id())).isEqualTo(sale);
    }
    void code(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.getCode()).isEqualTo(code));
    }

    @Test void acceptsMultipleExactPartialReturnsToDepletedBatchAndPreservesAllOriginalHistory() throws Exception {
        var originalMovement = jdbc.queryForMap("select * from stock_movement where id = ?", sale.lines().getFirst().allocations().getFirst().movementId());
        var allocations = jdbc.queryForList("select * from sale_allocation where line_id = ?", sale.lines().getFirst().id());
        var first = accept(UUID.randomUUID(), "1.000001");
        var second = accept(UUID.randomUUID(), "2");
        assertThat(balance()).isEqualByComparingTo("3.000001"); assertThat(movementCount()).isEqualTo(3);
        assertThat(first.id()).isNotEqualTo(second.id()); assertThat(first.movementId()).isNotEqualTo(second.movementId());
        assertThat(corrections.list(sale.id())).containsExactlyInAnyOrder(first, second);
        for (var c : List.of(first, second)) {
            var movement = jdbc.queryForMap("select * from stock_movement where id = ?", c.movementId());
            assertThat(movement).containsEntry("movement_type", "ADJUSTMENT_IN").containsEntry("reference_type", "SALE")
                .containsEntry("reference_id", sale.id()).containsEntry("reference_line_id", c.lineId()).containsEntry("batch_id", batch)
                .containsEntry("reason", "Inspected return");
            assertThat((BigDecimal) movement.get("quantity")).isEqualByComparingTo(c.quantity());
            assertThat(c.inspected()).isTrue(); assertThat(c.accepted()).isTrue(); assertThat(c.actor()).isEqualTo("operator");
            assertThat(c.occurredAt()).isNotNull();
        }
        assertThat(jdbc.queryForMap("select * from stock_movement where id = ?", sale.lines().getFirst().allocations().getFirst().movementId())).isEqualTo(originalMovement);
        assertThat(jdbc.queryForList("select * from sale_allocation where line_id = ?", sale.lines().getFirst().id())).isEqualTo(allocations);
        assertThat(orders.getById(sale.id())).isEqualTo(sale);
        code(() -> accept(UUID.randomUUID(), "0.000001"), "SALE_CORRECTION_LIMIT_EXCEEDED");
        assertThat(balance()).isEqualByComparingTo("3.000001");
        for (var sql : List.of("update sale_stock_correction set quantity = 1", "delete from sale_stock_correction")) {
            assertThatThrownBy(() -> jdbc.update(sql + " where sale_id = ?", sale.id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        }
        mvc.perform(get(url())).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].saleId").value(sale.id().toString()))
            .andExpect(jsonPath("$[0].inspected").value(true)).andExpect(jsonPath("$[0].accepted").value(true));
    }

    @Test void overReturnAfterPartialRejectsAtomically() {
        accept(UUID.randomUUID(), "2");
        code(() -> accept(UUID.randomUUID(), "1.000002"), "SALE_CORRECTION_LIMIT_EXCEEDED");
        assertThat(balance()).isEqualByComparingTo("2"); assertThat(corrections.list(sale.id())).hasSize(1); assertThat(movementCount()).isEqualTo(2);
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "0.0000001", "10000000000000"})
    void rejectsInvalidExactQuantities(String quantity) {
        code(() -> accept(UUID.randomUUID(), quantity), "INVALID_SALE_CORRECTION"); noReturn();
    }

    @Test void rejectsNullQuantityAndReasonAndMissingOrRejectedDecisions() {
        var valid = request("1");
        for (var invalid : List.of(
            new RegisterSaleStockCorrection(valid.lineId(), batch, null, "Reason", true, true),
            new RegisterSaleStockCorrection(valid.lineId(), batch, BigDecimal.ONE, null, true, true),
            new RegisterSaleStockCorrection(valid.lineId(), batch, BigDecimal.ONE, " ", true, true),
            new RegisterSaleStockCorrection(valid.lineId(), batch, BigDecimal.ONE, "x".repeat(256), true, true),
            new RegisterSaleStockCorrection(valid.lineId(), batch, BigDecimal.ONE, "Reason", false, true),
            new RegisterSaleStockCorrection(valid.lineId(), batch, BigDecimal.ONE, "Reason", true, false),
            new RegisterSaleStockCorrection(valid.lineId(), batch, BigDecimal.ONE, "Reason", null, true),
            new RegisterSaleStockCorrection(valid.lineId(), batch, BigDecimal.ONE, "Reason", true, null))) {
            code(() -> corrections.register(sale.id(), UUID.randomUUID(), invalid, "operator"), "INVALID_SALE_CORRECTION");
        }
        noReturn();
    }

    @ParameterizedTest @ValueSource(ints = {-1, 0, 1})
    void expirationUsesClockWithTodayExcluded(int days) {
        jdbc.update("update inventory_batch set expires_at = ? where id = ?", LocalDate.now(clock).plusDays(days), batch);
        if (days > 0) { accept(UUID.randomUUID(), "1"); assertThat(balance()).isEqualByComparingTo("1"); }
        else { code(() -> accept(UUID.randomUUID(), "1"), "SALE_RETURN_INELIGIBLE"); noReturn(); }
    }

    @ParameterizedTest @ValueSource(strings = {"undated", "inactive", "category", "unit"})
    void rejectsIneligibleBatchOrCurrentItem(String kind) {
        switch (kind) {
            case "undated" -> jdbc.update("update inventory_batch set expires_at = null where id = ?", batch);
            case "inactive" -> jdbc.update("update inventory_item set active = false where id = ?", item);
            case "category" -> jdbc.update("update inventory_item set category = 'CHEMICAL_INPUT' where id = ?", item);
            case "unit" -> jdbc.update("update inventory_item set default_unit = 'LITER' where id = ?", item);
        }
        code(() -> accept(UUID.randomUUID(), "1"), "SALE_RETURN_INELIGIBLE"); noReturn();
    }

    @Test void rejectsUnknownAndMismatchedReferencesAndNonconfirmedSales() {
        code(() -> corrections.register(UUID.randomUUID(), UUID.randomUUID(), request("1"), "operator"), "ORDER_NOT_FOUND");
        var lineId = sale.lines().getFirst().id();
        for (var r : List.of(
            new RegisterSaleStockCorrection(UUID.randomUUID(), batch, BigDecimal.ONE, "Reason", true, true),
            new RegisterSaleStockCorrection(lineId, UUID.randomUUID(), BigDecimal.ONE, "Reason", true, true)))
            code(() -> corrections.register(sale.id(), UUID.randomUUID(), r, "operator"), "SALE_CORRECTION_ALLOCATION_INVALID");
        var draft = orders.register(new SaveDraftCommand(null, List.of(new SaveDraftCommand.Line(null, item, BigDecimal.ONE, BigDecimal.ONE))));
        code(() -> corrections.register(draft.id(), UUID.randomUUID(), request("1"), "operator"), "SALE_CORRECTION_NOT_ALLOWED");
        orders.cancel(draft.id());
        code(() -> corrections.register(draft.id(), UUID.randomUUID(), request("1"), "operator"), "SALE_CORRECTION_NOT_ALLOWED");
        var other = UUID.randomUUID();
        jdbc.update("insert into inventory_batch (id,inventory_item_id,initial_quantity,current_quantity,received_at,expires_at) values (?, ?, 5, 5, ?, ?)", other, item, LocalDate.now(clock), LocalDate.now(clock).plusDays(3));
        code(() -> corrections.register(sale.id(), UUID.randomUUID(), new RegisterSaleStockCorrection(lineId, other, BigDecimal.ONE, "Reason", true, true), "operator"), "SALE_CORRECTION_ALLOCATION_INVALID");
        var otherSale = orders.confirm(draftForOne().id());
        code(() -> corrections.register(sale.id(), UUID.randomUUID(), new RegisterSaleStockCorrection(otherSale.lines().getFirst().id(), other, BigDecimal.ONE, "Reason", true, true), "operator"), "SALE_CORRECTION_ALLOCATION_INVALID");
        noReturn();
    }
    OrderResult draftForOne() { return orders.register(new SaveDraftCommand(null, List.of(new SaveDraftCommand.Line(null, item, BigDecimal.ONE, BigDecimal.ONE)))); }

    @Test void idempotentLostResponseRetryReturnsOriginalResultAfterExpiryAndRejectsChangedPayload() {
        var id = UUID.randomUUID(); var saved = accept(id, "1.000001");
        jdbc.update("update inventory_batch set expires_at = ? where id = ?", LocalDate.now(clock), batch);
        assertThat(corrections.register(sale.id(), id, request("1.000001"), "another-operator")).isEqualTo(saved);
        var r = request("1.000001");
        for (var changed : List.of(request("1"),
            new RegisterSaleStockCorrection(r.lineId(), batch, r.quantity(), "Other reason", true, true),
            new RegisterSaleStockCorrection(UUID.randomUUID(), batch, r.quantity(), r.reason(), true, true),
            new RegisterSaleStockCorrection(r.lineId(), UUID.randomUUID(), r.quantity(), r.reason(), true, true)))
            code(() -> corrections.register(sale.id(), id, changed, "operator"), "SALE_CORRECTION_ID_CONFLICT");
        assertThat(balance()).isEqualByComparingTo("1.000001"); assertThat(movementCount()).isEqualTo(2);
    }

    @ParameterizedTest @CsvSource({"true, 2", "false, 2", "false, 1"})
    void concurrentRequestsSerializeWithoutDuplicateOrInflatedCredits(boolean identicalId, String quantity) throws Exception {
        var id = UUID.randomUUID();
        var held = new CountDownLatch(1); var release = new CountDownLatch(1); var started = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> new TransactionTemplate(transactions).execute(tx -> {
                var result = accept(id, quantity); held.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("release timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                return result;
            }));
            try {
                assertThat(held.await(5, TimeUnit.SECONDS)).isTrue();
                var second = executor.submit(() -> { started.countDown(); return accept(identicalId ? id : UUID.randomUUID(), quantity); });
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> second.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                release.countDown(); var result = first.get(5, TimeUnit.SECONDS);
                if (identicalId) assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo(result);
                else if (quantity.equals("1")) assertThat(second.get(5, TimeUnit.SECONDS).movementId()).isNotEqualTo(result.movementId());
                else assertThatThrownBy(() -> second.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(OrderException.class)
                    .satisfies(e -> assertThat(((DomainException)e.getCause()).getCode()).isEqualTo("SALE_CORRECTION_LIMIT_EXCEEDED"));
            } finally { release.countDown(); }
        }
        assertThat(balance()).isEqualByComparingTo("2");
        var acceptedCount = !identicalId && quantity.equals("1") ? 2 : 1;
        assertThat(movementCount()).isEqualTo(1 + acceptedCount); assertThat(corrections.list(sale.id())).hasSize(acceptedCount);
    }

    @ParameterizedTest @ValueSource(strings = {"sale_stock_correction", "stock_movement"})
    void persistenceFailureRollsBackCorrectionMovementAndBalanceAndAllowsExplicitRetry(String table) throws Exception {
        var id = UUID.randomUUID();
        jdbc.execute("create function issue276_fail() returns trigger language plpgsql as $$ begin raise exception 'injected failure'; end; $$");
        jdbc.execute("create trigger issue276_fail before insert on " + table + " for each row execute function issue276_fail()");
        try {
            mvc.perform(put(url() + "/" + id).with(csrf()).contentType("application/json").content(body("1")))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("ORDER_PERSISTENCE_FAILED"));
            noReturn();
        } finally {
            jdbc.execute("drop trigger issue276_fail on " + table); jdbc.execute("drop function issue276_fail()");
        }
        accept(id, "1"); assertThat(balance()).isEqualByComparingTo("1");
    }

    @Test void securityCsrfExactHttpContractAndOpenApi() throws Exception {
        var id = UUID.randomUUID(); var write = url() + "/" + id;
        mvc.perform(put(write).with(anonymous()).with(csrf()).contentType("application/json").content(body("1"))).andExpect(status().isUnauthorized());
        mvc.perform(put(write).with(user("viewer").authorities(() -> "VIEWER")).with(csrf()).contentType("application/json").content(body("1"))).andExpect(status().isForbidden());
        mvc.perform(put(write).contentType("application/json").content(body("1"))).andExpect(status().isForbidden());
        mvc.perform(get(url()).with(anonymous())).andExpect(status().isUnauthorized());
        mvc.perform(get(url()).with(user("viewer").authorities(() -> "VIEWER"))).andExpect(status().isForbidden());
        noReturn();
        mvc.perform(put(write).with(csrf()).contentType("application/json").content(body("0")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_SALE_CORRECTION"));
        var response = mvc.perform(put(write).with(csrf()).contentType("application/json").content(body("1.000001")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.quantity").value("1.000001")).andExpect(jsonPath("$.actor").value("user"))
            .andExpect(jsonPath("$.movementId").isNotEmpty()).andReturn().getResponse().getContentAsString();
        mvc.perform(put(write).with(csrf()).contentType("application/json").content(body("1.000001")))
            .andExpect(status().isOk()).andExpect(content().json(response));
        assertThat(balance()).isEqualByComparingTo("1.000001");
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/sales/{orderId}/stock-corrections/{correctionId}'].put.responses['409']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/sales/{orderId}/stock-corrections/{correctionId}'].put.responses['422']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/sales/{orderId}/stock-corrections'].get").exists());
    }

    @Test void inventoryContractRequiresCallerTransaction() {
        assertThatThrownBy(() -> inventory.credit(item, batch, BigDecimal.ONE, "Reason", null))
            .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }
    String url() { return "/api/v1/sales/" + sale.id() + "/stock-corrections"; }
    String body(String quantity) {
        return """
            {"lineId":"%s","batchId":"%s","quantity":"%s","reason":"Physical return","inspected":true,"accepted":true}
            """.formatted(sale.lines().getFirst().id(), batch, quantity);
    }
}
