package com.ceudelavanda.lavandaflow.production.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import com.ceudelavanda.lavandaflow.production.application.history.GetProductionExecutionHistoryQuery;
import com.ceudelavanda.lavandaflow.production.application.history.ProductionExecutionHistoryQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class JdbcProductionExecutionHistoryQueryTest {

    private static final LocalDate FIRST_DATE = LocalDate.of(2040, 4, 10);
    private static final LocalDate SECOND_DATE = LocalDate.of(2040, 4, 11);
    private static final LocalDate DETAIL_DATE = LocalDate.of(2040, 5, 12);

    @Autowired private ProductionExecutionHistoryQuery historyQuery;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void shouldApplyInclusiveDatesFixedOrderingAndPaginationWithoutConsumptions() {
        var fixture = fixture();
        var lowestId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        var middleId = UUID.fromString("00000000-0000-0000-0000-000000000102");
        var highestId = UUID.fromString("00000000-0000-0000-0000-000000000103");
        var newestDateId = UUID.fromString("00000000-0000-0000-0000-000000000104");
        insertExecution(lowestId, fixture, FIRST_DATE, "2040-04-10T10:00:00Z", "1.100001");
        insertExecution(middleId, fixture, FIRST_DATE, "2040-04-10T11:00:00Z", "2.200002");
        insertExecution(highestId, fixture, FIRST_DATE, "2040-04-10T11:00:00Z", "3.300003");
        insertExecution(newestDateId, fixture, SECOND_DATE, "2040-04-09T01:00:00Z", "4.400004");

        var all = historyQuery.find(new GetProductionExecutionHistoryQuery(
            FIRST_DATE, SECOND_DATE, 0, 20
        ));
        assertThat(all.content())
            .extracting(ProductionExecutionHistoryQuery.Summary::executionId)
            .containsExactly(newestDateId, highestId, middleId, lowestId);
        assertThat(all.content())
            .extracting(ProductionExecutionHistoryQuery.Summary::outputQuantity)
            .containsExactly(
                new java.math.BigDecimal("4.400004"),
                new java.math.BigDecimal("3.300003"),
                new java.math.BigDecimal("2.200002"),
                new java.math.BigDecimal("1.100001")
            );
        assertThat(all.totalElements()).isEqualTo(4);

        var firstPage = historyQuery.find(new GetProductionExecutionHistoryQuery(
            FIRST_DATE, SECOND_DATE, 0, 2
        ));
        var secondPage = historyQuery.find(new GetProductionExecutionHistoryQuery(
            FIRST_DATE, SECOND_DATE, 1, 2
        ));
        assertThat(firstPage.content()).extracting(ProductionExecutionHistoryQuery.Summary::executionId)
            .containsExactly(newestDateId, highestId);
        assertThat(secondPage.content()).extracting(ProductionExecutionHistoryQuery.Summary::executionId)
            .containsExactly(middleId, lowestId);
        assertThat(firstPage.totalPages()).isEqualTo(2);

        assertThat(historyQuery.find(new GetProductionExecutionHistoryQuery(
            FIRST_DATE, FIRST_DATE, 0, 20
        )).content()).hasSize(3);
        assertThat(historyQuery.find(new GetProductionExecutionHistoryQuery(
            SECOND_DATE, null, 0, 20
        )).content()).singleElement()
            .extracting(ProductionExecutionHistoryQuery.Summary::executionId)
            .isEqualTo(newestDateId);
        assertThat(historyQuery.find(new GetProductionExecutionHistoryQuery(
            null, FIRST_DATE, 0, 20
        )).content()).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void shouldReadPersistedDetailAndConsumptionPositionExactly() {
        var fixture = fixture();
        var executionId = UUID.randomUUID();
        insertExecution(
            executionId, fixture, DETAIL_DATE, "2040-05-12T12:30:00Z", "10.123456"
        );
        var first = source("FIRST");
        var second = source("SECOND");
        insertConsumption(executionId, 1, second, "2.000002");
        insertConsumption(executionId, 0, first, "1.000001");

        var detail = historyQuery.findById(executionId).orElseThrow();

        assertThat(detail.outputQuantity()).isEqualByComparingTo("10.123456");
        assertThat(detail.productionDate()).isEqualTo(DETAIL_DATE);
        assertThat(detail.outputReceivedAt()).isEqualTo(DETAIL_DATE);
        assertThat(detail.outputExpiresAt()).isNull();
        assertThat(detail.completedAt()).isEqualTo(Instant.parse("2040-05-12T12:30:00Z"));
        assertThat(detail.consumptions())
            .extracting(ProductionExecutionHistoryQuery.Consumption::sourceBatchId)
            .containsExactly(first.batchId(), second.batchId());
        assertThat(detail.consumptions())
            .extracting(ProductionExecutionHistoryQuery.Consumption::quantity)
            .containsExactly(
                new java.math.BigDecimal("1.000001"),
                new java.math.BigDecimal("2.000002")
            );
        assertThat(historyQuery.findById(UUID.randomUUID())).isEmpty();
    }

    private Fixture fixture() {
        var outputItemId = insertItem("History output");
        var formulaId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into production_formula (id, output_inventory_item_id, output_quantity, output_unit_of_measure, formula_kind) values (?, ?, 1, 'MILLILITER', 'STANDARD')",
            formulaId, outputItemId
        );
        return new Fixture(formulaId, outputItemId);
    }

    private void insertExecution(
        UUID executionId,
        Fixture fixture,
        LocalDate productionDate,
        String completedAt,
        String quantity
    ) {
        var outputBatchId = insertBatch(fixture.outputItemId(), "OUTPUT-" + executionId);
        jdbcTemplate.update(
            """
                insert into production_execution (
                    id, formula_id, output_inventory_item_id, output_batch_id, output_quantity,
                    lot_code, lot_code_mode, production_date, output_received_at,
                    output_expires_at, completed_at
                ) values (?, ?, ?, ?, ?, ?, 'MANUAL', ?, ?, null, ?::timestamptz)
                """,
            executionId, fixture.formulaId(), fixture.outputItemId(), outputBatchId,
            new java.math.BigDecimal(quantity), "LOT-" + executionId, productionDate,
            productionDate, completedAt
        );
    }

    private Source source(String lotCode) {
        var itemId = insertItem("History source " + lotCode);
        var batchId = insertBatch(itemId, lotCode);
        var movementId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into stock_movement (id, batch_id, movement_type, quantity, occurred_at) values (?, ?, 'CONSUMPTION', 1, now())",
            movementId, batchId
        );
        return new Source(itemId, batchId, movementId);
    }

    private void insertConsumption(UUID executionId, int position, Source source, String quantity) {
        jdbcTemplate.update(
            """
                insert into production_consumption (
                    execution_id, position, source_batch_id, source_inventory_item_id,
                    movement_id, quantity
                ) values (?, ?, ?, ?, ?, ?)
                """,
            executionId, position, source.batchId(), source.itemId(), source.movementId(),
            new java.math.BigDecimal(quantity)
        );
    }

    private UUID insertItem(String name) {
        var id = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into inventory_item (id, name, category, default_unit, active) values (?, ?, 'OTHER', 'MILLILITER', true)",
            id, name + " " + id
        );
        return id;
    }

    private UUID insertBatch(UUID itemId, String lotCode) {
        var id = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into inventory_batch (
                    id, inventory_item_id, lot_code, initial_quantity, current_quantity, received_at
                ) values (?, ?, ?, 1, 1, ?)
                """,
            id, itemId, lotCode, FIRST_DATE
        );
        return id;
    }

    private record Fixture(UUID formulaId, UUID outputItemId) {
    }

    private record Source(UUID itemId, UUID batchId, UUID movementId) {
    }
}
