package com.ceudelavanda.lavandaflow.production.application.lot;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AllocateInternalProductionLotCodeIntegrationTest {

    @Autowired
    private AllocateInternalProductionLotCode allocateInternalProductionLotCode;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        jdbcTemplate.update("delete from production_lot_sequence");
    }

    @Test
    void shouldRequireAnEncompassingProductionTransaction() {
        var outputItemId = insertCatalogItem("BDS");

        assertThatThrownBy(() -> allocateInternalProductionLotCode.execute(command(outputItemId, 2026, 9)))
            .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void shouldStartAtOneAndIncrementWithinTheSameScope() {
        var outputItemId = insertCatalogItem("BDS");

        assertThat(allocate(outputItemId, 2026, 9)).isEqualTo("BDS-000-001-09-2026");
        assertThat(allocate(outputItemId, 2026, 9)).isEqualTo("BDS-000-002-09-2026");
    }

    @Test
    void shouldKeepDifferentProductionTypeAndEssencePrefixesIndependent() {
        var bodySplash = insertCatalogItem("BDS", "901");
        var soap = insertCatalogItem("SBN", "902");
        var otherEssence = insertCatalogItem("BDS", "903");

        assertThat(allocate(bodySplash, 2026, 9)).isEqualTo("BDS-901-001-09-2026");
        assertThat(allocate(soap, 2026, 9)).isEqualTo("SBN-902-001-09-2026");
        assertThat(allocate(otherEssence, 2026, 9)).isEqualTo("BDS-903-001-09-2026");
    }

    @Test
    void shouldResetForNewCalendarMonthAndYear() {
        var outputItemId = insertCatalogItem("BDS");

        assertThat(allocate(outputItemId, 2026, 9)).isEqualTo("BDS-000-001-09-2026");
        assertThat(allocate(outputItemId, 2026, 10)).isEqualTo("BDS-000-001-10-2026");
        assertThat(allocate(outputItemId, 2027, 9)).isEqualTo("BDS-000-001-09-2027");
    }

    @Test
    void shouldFailAtExhaustionWithoutChangingCommittedSequenceState() {
        var outputItemId = insertCatalogItem("BDS");
        jdbcTemplate.update(
            """
                insert into production_lot_sequence (
                    production_type_code, essence_reference, production_year, production_month, last_sequence
                ) values (?, ?, ?, ?, ?)
                """,
            "BDS", "000", 2026, 9, 999
        );

        assertThatThrownBy(() -> allocate(outputItemId, 2026, 9))
            .isInstanceOf(ProductionLotSequenceExhaustedException.class);

        assertThat(lastSequence("BDS", "000", 2026, 9)).isEqualTo(999);
    }

    @Test
    void shouldRestoreSequenceStateWhenTheProductionTransactionRollsBack() {
        var outputItemId = insertCatalogItem("BDS");

        var rolledBackCode = transactionTemplate.execute(status -> {
            var code = allocateInternalProductionLotCode.execute(command(outputItemId, 2026, 9)).value();
            status.setRollbackOnly();
            return code;
        });

        assertThat(rolledBackCode).isEqualTo("BDS-000-001-09-2026");
        assertThat(allocate(outputItemId, 2026, 9)).isEqualTo("BDS-000-001-09-2026");
    }

    @Test
    void shouldRestoreIncrementWhenTheProductionTransactionRollsBack() {
        var outputItemId = insertCatalogItem("BDS");
        assertThat(allocate(outputItemId, 2026, 9)).isEqualTo("BDS-000-001-09-2026");

        var rolledBackCode = transactionTemplate.execute(status -> {
            var code = allocateInternalProductionLotCode.execute(command(outputItemId, 2026, 9)).value();
            status.setRollbackOnly();
            return code;
        });

        assertThat(rolledBackCode).isEqualTo("BDS-000-002-09-2026");
        assertThat(allocate(outputItemId, 2026, 9)).isEqualTo("BDS-000-002-09-2026");
    }

    @Test
    void shouldAllocateDistinctCodesForConcurrentTransactionsCreatingTheSameScope() throws Exception {
        var outputItemId = insertCatalogItem("BDS");

        assertThat(allocateConcurrently(outputItemId))
            .containsExactlyInAnyOrder("BDS-000-001-09-2026", "BDS-000-002-09-2026");
    }

    @Test
    void shouldAllocateDistinctCodesForConcurrentTransactionsOnAnExistingScope() throws Exception {
        var outputItemId = insertCatalogItem("BDS");
        assertThat(allocate(outputItemId, 2026, 9)).isEqualTo("BDS-000-001-09-2026");

        assertThat(allocateConcurrently(outputItemId))
            .containsExactlyInAnyOrder("BDS-000-002-09-2026", "BDS-000-003-09-2026");
    }

    private List<String> allocateConcurrently(UUID outputItemId) throws Exception {
        var transactionsReady = new CountDownLatch(2);
        var allocationStart = new CountDownLatch(1);
        var allocationAttempts = new CountDownLatch(2);
        var firstAllocationCompleted = new CountDownLatch(1);
        var completedAllocations = new CountDownLatch(2);
        var commit = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);

        try {
            var first = executor.submit(() -> allocateInConcurrentTransaction(
                outputItemId,
                transactionsReady,
                allocationStart,
                allocationAttempts,
                firstAllocationCompleted,
                completedAllocations,
                commit
            ));
            var second = executor.submit(() -> allocateInConcurrentTransaction(
                outputItemId,
                transactionsReady,
                allocationStart,
                allocationAttempts,
                firstAllocationCompleted,
                completedAllocations,
                commit
            ));
            assertThat(transactionsReady.await(5, TimeUnit.SECONDS)).isTrue();
            allocationStart.countDown();
            assertThat(allocationAttempts.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(firstAllocationCompleted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(completedAllocations.getCount()).isEqualTo(1);
            commit.countDown();

            return List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
        } finally {
            allocationStart.countDown();
            commit.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private String allocateInConcurrentTransaction(
        UUID outputItemId,
        CountDownLatch transactionsReady,
        CountDownLatch allocationStart,
        CountDownLatch allocationAttempts,
        CountDownLatch firstAllocationCompleted,
        CountDownLatch completedAllocations,
        CountDownLatch commit
    ) {
        return transactionTemplate.execute(status -> {
            transactionsReady.countDown();
            await(allocationStart, "concurrent allocation start");
            allocationAttempts.countDown();
            var code = allocateInternalProductionLotCode.execute(command(outputItemId, 2026, 9)).value();
            completedAllocations.countDown();
            firstAllocationCompleted.countDown();
            await(commit, "concurrent allocation commit");
            return code;
        });
    }

    private static void await(CountDownLatch latch, String operation) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for " + operation);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for " + operation, exception);
        }
    }

    private String allocate(UUID outputItemId, int year, int month) {
        return transactionTemplate.execute(status -> allocateInternalProductionLotCode.execute(
            command(outputItemId, year, month)
        )).value();
    }

    private UUID insertCatalogItem(String productionTypeCode) {
        return insertCatalogItem(productionTypeCode, null);
    }

    private UUID insertCatalogItem(String productionTypeCode, String essenceReference) {
        var id = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into inventory_item (
                    id, name, category, default_unit, active, essence_reference, production_type_code
                ) values (?, ?, ?, ?, ?, ?, ?)
                """,
            id,
            "Lot allocator " + UUID.randomUUID(),
            essenceReference == null ? "OTHER" : "ESSENCE",
            "LITER",
            true,
            essenceReference,
            productionTypeCode
        );
        return id;
    }

    private int lastSequence(String productionTypeCode, String essenceReference, int year, int month) {
        return jdbcTemplate.queryForObject(
            """
                select last_sequence from production_lot_sequence
                where production_type_code = ? and essence_reference = ?
                  and production_year = ? and production_month = ?
                """,
            Integer.class,
            productionTypeCode,
            essenceReference,
            year,
            month
        );
    }

    private static AllocateInternalProductionLotCodeCommand command(UUID outputItemId, int year, int month) {
        return new AllocateInternalProductionLotCodeCommand(outputItemId, LocalDate.of(year, month, 3));
    }
}
