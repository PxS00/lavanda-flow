package com.ceudelavanda.lavandaflow.production.application.execution;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItem;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItemRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.Batch;
import com.ceudelavanda.lavandaflow.inventory.domain.BatchRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.MovementType;
import com.ceudelavanda.lavandaflow.inventory.domain.StockMovementRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.ExpiredBatchException;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InsufficientStockException;
import com.ceudelavanda.lavandaflow.production.domain.FormulaIngredient;
import com.ceudelavanda.lavandaflow.production.domain.ProductionFormula;
import com.ceudelavanda.lavandaflow.production.domain.ProductionFormulaKind;
import com.ceudelavanda.lavandaflow.production.domain.ProductionFormulaRepository;
import com.ceudelavanda.lavandaflow.production.domain.ProductionLotCodeMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import({
    TestcontainersConfiguration.class,
    PackagedProductionFillingIntegrationTest.FixedClockConfiguration.class
})
class PackagedProductionFillingIntegrationTest {

    @Autowired private RegisterProduction registerProduction;
    @Autowired private InventoryItemRepository inventoryItemRepository;
    @Autowired private BatchRepository batchRepository;
    @Autowired private StockMovementRepository stockMovementRepository;
    @Autowired private ProductionFormulaRepository productionFormulaRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetPackagedSequence() {
        jdbcTemplate.update("DELETE FROM packaged_production_lot_sequence");
    }

    @Test
    void shouldFillMultiplePackagedUnitsFromMixedUnitsAndMultipleBulkBatches() {
        var output = item("Packaged perfume", Category.FINISHED_PRODUCT, UnitOfMeasure.UNIT);
        var bulk = item("Bulk perfume", Category.FINISHED_PRODUCT, UnitOfMeasure.MILLILITER);
        var bottle = item("Bottle", Category.BOTTLE, UnitOfMeasure.UNIT);
        var formula = packagedFormula(output, bulk, bottle);
        var firstBulk = batch(bulk.getId(), "60", null);
        var secondBulk = batch(bulk.getId(), "90", null);
        var bottles = batch(bottle.getId(), "5", null);

        var result = registerProduction.execute(command(
            formula.getId(),
            "5",
            List.of(
                allocation(firstBulk, "60"),
                allocation(secondBulk, "90"),
                allocation(bottles, "5")
            ),
            LocalDate.of(2026, 9, 12)
        ));

        assertThat(result.lotCode()).isEqualTo("001-09-2026");
        assertThat(result.consumptions()).hasSize(3);
        assertThat(balance(firstBulk)).isEqualByComparingTo("0");
        assertThat(balance(secondBulk)).isEqualByComparingTo("0");
        assertThat(balance(bottles)).isEqualByComparingTo("0");

        var outputBatch = batchRepository.findById(result.outputBatchId()).orElseThrow();
        assertThat(outputBatch.getInventoryItemId()).isEqualTo(output.getId());
        assertThat(outputBatch.getInitialQuantity()).isEqualByComparingTo("5");
        assertThat(outputBatch.getLotCode()).isEqualTo("001-09-2026");
        assertThat(stockMovementRepository.findByBatchIdOrderByOccurredAtAsc(outputBatch.getId()))
            .singleElement().extracting(movement -> movement.type()).isEqualTo(MovementType.ENTRY);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM production_consumption WHERE execution_id = ?",
            Integer.class,
            result.executionId()
        )).isEqualTo(3);
    }

    @Test
    void shouldShareSequenceAcrossPackagedFormulasAndResetForNewMonth() {
        var bulkA = item("Bulk A", Category.FINISHED_PRODUCT, UnitOfMeasure.MILLILITER);
        var bulkB = item("Bulk B", Category.FINISHED_PRODUCT, UnitOfMeasure.MILLILITER);
        var outputA = item("Packaged A", Category.FINISHED_PRODUCT, UnitOfMeasure.UNIT);
        var outputB = item("Packaged B", Category.FINISHED_PRODUCT, UnitOfMeasure.UNIT);
        var bottle = item("Shared bottle", Category.BOTTLE, UnitOfMeasure.UNIT);
        var formulaA = packagedFormula(outputA, bulkA, bottle);
        var formulaB = packagedFormula(outputB, bulkB, bottle);

        var first = executeSingleUnit(formulaA, bulkA, bottle, LocalDate.of(2026, 9, 12));
        var second = executeSingleUnit(formulaB, bulkB, bottle, LocalDate.of(2026, 9, 13));
        var october = executeSingleUnit(formulaA, bulkA, bottle, LocalDate.of(2026, 10, 1));

        assertThat(first.lotCode()).isEqualTo("001-09-2026");
        assertThat(second.lotCode()).isEqualTo("002-09-2026");
        assertThat(october.lotCode()).isEqualTo("001-10-2026");
    }

    @Test
    void shouldRollbackPackagedSequenceWhenStockApplicationFails() {
        var output = item("Packaged rollback", Category.FINISHED_PRODUCT, UnitOfMeasure.UNIT);
        var bulk = item("Bulk rollback", Category.FINISHED_PRODUCT, UnitOfMeasure.MILLILITER);
        var bottle = item("Bottle rollback", Category.BOTTLE, UnitOfMeasure.UNIT);
        var formula = packagedFormula(output, bulk, bottle);
        var insufficientBulk = batch(bulk.getId(), "29", null);
        var firstBottle = batch(bottle.getId(), "1", null);

        assertThatThrownBy(() -> registerProduction.execute(command(
            formula.getId(),
            "1",
            List.of(allocation(insufficientBulk, "30"), allocation(firstBottle, "1")),
            LocalDate.of(2026, 9, 12)
        ))).isInstanceOf(InsufficientStockException.class);

        var enoughBulk = batch(bulk.getId(), "30", null);
        var secondBottle = batch(bottle.getId(), "1", null);
        var result = registerProduction.execute(command(
            formula.getId(),
            "1",
            List.of(allocation(enoughBulk, "30"), allocation(secondBottle, "1")),
            LocalDate.of(2026, 9, 12)
        ));

        assertThat(result.lotCode()).isEqualTo("001-09-2026");
    }

    @Test
    void shouldRejectExpiredBulkWithoutPartialPackagedOutput() {
        var output = item("Packaged expired", Category.FINISHED_PRODUCT, UnitOfMeasure.UNIT);
        var bulk = item("Bulk expired", Category.FINISHED_PRODUCT, UnitOfMeasure.MILLILITER);
        var bottle = item("Bottle expired", Category.BOTTLE, UnitOfMeasure.UNIT);
        var formula = packagedFormula(output, bulk, bottle);
        var expiredBulk = batch(bulk.getId(), "30", LocalDate.of(2026, 9, 11));
        var bottles = batch(bottle.getId(), "1", null);

        assertThatThrownBy(() -> registerProduction.execute(command(
            formula.getId(),
            "1",
            List.of(allocation(expiredBulk, "30"), allocation(bottles, "1")),
            LocalDate.of(2026, 9, 12)
        ))).isInstanceOf(ExpiredBatchException.class);

        assertThat(balance(expiredBulk)).isEqualByComparingTo("30");
        assertThat(balance(bottles)).isEqualByComparingTo("1");
        assertThat(batchRepository.findByInventoryItemId(output.getId())).isEmpty();
    }

    @Test
    void shouldAllocateDistinctPackagedCodesUnderConcurrentPressure() throws Exception {
        var bulkA = item("Concurrent bulk A", Category.FINISHED_PRODUCT, UnitOfMeasure.MILLILITER);
        var bulkB = item("Concurrent bulk B", Category.FINISHED_PRODUCT, UnitOfMeasure.MILLILITER);
        var outputA = item("Concurrent output A", Category.FINISHED_PRODUCT, UnitOfMeasure.UNIT);
        var outputB = item("Concurrent output B", Category.FINISHED_PRODUCT, UnitOfMeasure.UNIT);
        var bottleA = item("Concurrent bottle A", Category.BOTTLE, UnitOfMeasure.UNIT);
        var bottleB = item("Concurrent bottle B", Category.BOTTLE, UnitOfMeasure.UNIT);
        var formulaA = packagedFormula(outputA, bulkA, bottleA);
        var formulaB = packagedFormula(outputB, bulkB, bottleB);
        var bulkBatchA = batch(bulkA.getId(), "30", null);
        var bulkBatchB = batch(bulkB.getId(), "30", null);
        var bottleBatchA = batch(bottleA.getId(), "1", null);
        var bottleBatchB = batch(bottleB.getId(), "1", null);
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);

        try {
            var first = executor.submit(() -> {
                start.await();
                return registerProduction.execute(command(
                    formulaA.getId(),
                    "1",
                    List.of(allocation(bulkBatchA, "30"), allocation(bottleBatchA, "1")),
                    LocalDate.of(2026, 9, 12)
                ));
            });
            var second = executor.submit(() -> {
                start.await();
                return registerProduction.execute(command(
                    formulaB.getId(),
                    "1",
                    List.of(allocation(bulkBatchB, "30"), allocation(bottleBatchB, "1")),
                    LocalDate.of(2026, 9, 12)
                ));
            });

            start.countDown();
            var firstResult = first.get(10, TimeUnit.SECONDS);
            var secondResult = second.get(10, TimeUnit.SECONDS);

            assertThat(Set.of(firstResult.lotCode(), secondResult.lotCode()))
                .containsExactlyInAnyOrder("001-09-2026", "002-09-2026");
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private RegisterProductionResult executeSingleUnit(
        ProductionFormula formula,
        InventoryItem bulk,
        InventoryItem bottle,
        LocalDate productionDate
    ) {
        var bulkBatch = batch(bulk.getId(), "30", null);
        var bottleBatch = batch(bottle.getId(), "1", null);
        return registerProduction.execute(command(
            formula.getId(),
            "1",
            List.of(allocation(bulkBatch, "30"), allocation(bottleBatch, "1")),
            productionDate
        ));
    }

    private ProductionFormula packagedFormula(InventoryItem output, InventoryItem bulk, InventoryItem bottle) {
        return productionFormulaRepository.save(ProductionFormula.create(
            ProductionFormulaKind.PACKAGED_FILLING,
            output.getId(),
            BigDecimal.ONE,
            UnitOfMeasure.UNIT,
            List.of(
                new FormulaIngredient(bulk.getId(), new BigDecimal("30"), UnitOfMeasure.MILLILITER),
                new FormulaIngredient(bottle.getId(), BigDecimal.ONE, UnitOfMeasure.UNIT)
            )
        ));
    }

    private InventoryItem item(String name, Category category, UnitOfMeasure unit) {
        return inventoryItemRepository.save(InventoryItem.create(name + " " + UUID.randomUUID(), null, category, unit));
    }

    private Batch batch(UUID itemId, String quantity, LocalDate expiresAt) {
        return batchRepository.save(Batch.create(
            itemId,
            null,
            "SOURCE-" + UUID.randomUUID(),
            new BigDecimal(quantity),
            LocalDate.of(2026, 9, 1),
            expiresAt
        ));
    }

    private BigDecimal balance(Batch batch) {
        return batchRepository.findById(batch.getId()).orElseThrow().getCurrentQuantity();
    }

    private static ProductionSourceAllocationCommand allocation(Batch batch, String quantity) {
        return new ProductionSourceAllocationCommand(batch.getId(), new BigDecimal(quantity));
    }

    private static RegisterProductionCommand command(
        UUID formulaId,
        String outputQuantity,
        List<ProductionSourceAllocationCommand> allocations,
        LocalDate productionDate
    ) {
        return new RegisterProductionCommand(
            formulaId,
            new BigDecimal(outputQuantity),
            allocations,
            productionDate,
            productionDate,
            null,
            ProductionLotCodeMode.GENERATED,
            null
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock packagedProductionClock() {
            return Clock.fixed(
                Instant.parse("2026-09-12T12:00:00Z"),
                ZoneId.of("America/Sao_Paulo")
            );
        }
    }
}
