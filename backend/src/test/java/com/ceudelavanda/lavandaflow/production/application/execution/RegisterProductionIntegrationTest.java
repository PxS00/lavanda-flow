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
import com.ceudelavanda.lavandaflow.production.domain.ProductionFormulaRepository;
import com.ceudelavanda.lavandaflow.production.domain.ProductionLotCodeMode;
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
    RegisterProductionIntegrationTest.FixedClockConfiguration.class
})
class RegisterProductionIntegrationTest {

    @Autowired private RegisterProduction registerProduction;
    @Autowired private InventoryItemRepository inventoryItemRepository;
    @Autowired private BatchRepository batchRepository;
    @Autowired private StockMovementRepository stockMovementRepository;
    @Autowired private ProductionFormulaRepository productionFormulaRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void shouldRegisterGeneratedProductionWithMultipleSourceBatchesAndImmutableHistory() {
        var outputItem = item(Category.OTHER, "BDS");
        var ingredientItem = item(Category.ESSENCE, null);
        var formula = formula(outputItem.getId(), ingredientItem.getId(), "10", "10");
        var first = batch(ingredientItem.getId(), "4", null);
        var second = batch(ingredientItem.getId(), "6", null);

        var result = registerProduction.execute(command(
            formula.getId(),
            "10",
            List.of(
                new ProductionSourceAllocationCommand(first.getId(), new BigDecimal("4")),
                new ProductionSourceAllocationCommand(second.getId(), new BigDecimal("6"))
            ),
            ProductionLotCodeMode.GENERATED,
            null
        ));

        assertThat(result.lotCode()).isEqualTo("BDS-000-001-09-2026");
        assertThat(result.consumptions()).hasSize(2);
        assertThat(balance(first.getId())).isEqualByComparingTo("0");
        assertThat(balance(second.getId())).isEqualByComparingTo("0");
        assertThat(stockMovementRepository.findByBatchIdOrderByOccurredAtAsc(first.getId()))
            .singleElement().extracting(movement -> movement.type()).isEqualTo(MovementType.CONSUMPTION);
        assertThat(stockMovementRepository.findByBatchIdOrderByOccurredAtAsc(second.getId()))
            .singleElement().extracting(movement -> movement.type()).isEqualTo(MovementType.CONSUMPTION);

        var outputBatch = batchRepository.findById(result.outputBatchId()).orElseThrow();
        assertThat(outputBatch.getInventoryItemId()).isEqualTo(outputItem.getId());
        assertThat(outputBatch.getSupplierId()).isNull();
        assertThat(outputBatch.getInitialQuantity()).isEqualByComparingTo("10");
        assertThat(stockMovementRepository.findByBatchIdOrderByOccurredAtAsc(outputBatch.getId()))
            .singleElement().extracting(movement -> movement.type()).isEqualTo(MovementType.ENTRY);

        assertThat(count("SELECT count(*) FROM production_execution WHERE id = ?", result.executionId())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM production_consumption WHERE execution_id = ?", result.executionId())).isEqualTo(2);

        assertThatThrownBy(() -> jdbcTemplate.update(
            "UPDATE production_execution SET lot_code = ? WHERE id = ?",
            "REWRITTEN",
            result.executionId()
        )).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
            "DELETE FROM production_consumption WHERE execution_id = ?",
            result.executionId()
        )).isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldAllowManualLotWithoutAdvancingGeneratedSequence() {
        var outputItem = item(Category.OTHER, "BAS");
        var ingredientItem = item(Category.ESSENCE, null);
        var formula = formula(outputItem.getId(), ingredientItem.getId(), "5", "5");
        var manualSource = batch(ingredientItem.getId(), "5", null);
        var generatedSource = batch(ingredientItem.getId(), "5", null);

        var manual = registerProduction.execute(command(
            formula.getId(),
            "5",
            List.of(new ProductionSourceAllocationCommand(manualSource.getId(), new BigDecimal("5"))),
            ProductionLotCodeMode.MANUAL,
            "MANUAL-BAS-42"
        ));
        var generated = registerProduction.execute(command(
            formula.getId(),
            "5",
            List.of(new ProductionSourceAllocationCommand(generatedSource.getId(), new BigDecimal("5"))),
            ProductionLotCodeMode.GENERATED,
            null
        ));

        assertThat(manual.lotCode()).isEqualTo("MANUAL-BAS-42");
        assertThat(generated.lotCode()).isEqualTo("BAS-000-001-09-2026");
        assertThat(manual.outputBatchId()).isNotEqualTo(generated.outputBatchId());
    }

    @Test
    void shouldDelegateExpiredAndInsufficientExactStockToInventoryAuthority() {
        var outputItem = item(Category.OTHER, "EXP");
        var ingredientItem = item(Category.ESSENCE, null);
        var formula = formula(outputItem.getId(), ingredientItem.getId(), "5", "5");
        var expired = batch(ingredientItem.getId(), "5", LocalDate.of(2026, 9, 3));
        var insufficient = batch(ingredientItem.getId(), "4", null);

        assertThatThrownBy(() -> registerProduction.execute(command(
            formula.getId(),
            "5",
            List.of(new ProductionSourceAllocationCommand(expired.getId(), new BigDecimal("5"))),
            ProductionLotCodeMode.MANUAL,
            "EXP-EXPIRED"
        ))).isInstanceOf(ExpiredBatchException.class);

        assertThatThrownBy(() -> registerProduction.execute(command(
            formula.getId(),
            "5",
            List.of(new ProductionSourceAllocationCommand(insufficient.getId(), new BigDecimal("5"))),
            ProductionLotCodeMode.MANUAL,
            "EXP-INSUFFICIENT"
        ))).isInstanceOf(InsufficientStockException.class);

        assertThat(balance(expired.getId())).isEqualByComparingTo("5");
        assertThat(balance(insufficient.getId())).isEqualByComparingTo("4");
        assertThat(batchRepository.findByInventoryItemId(outputItem.getId())).isEmpty();
    }

    @Test
    void shouldAllocateDistinctGeneratedCodesAndOutputBatchesUnderConcurrentPressure() throws Exception {
        var outputItem = item(Category.OTHER, "CNC");
        var ingredientItem = item(Category.ESSENCE, null);
        var formula = formula(outputItem.getId(), ingredientItem.getId(), "5", "5");
        var firstSource = batch(ingredientItem.getId(), "5", null);
        var secondSource = batch(ingredientItem.getId(), "5", null);
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);

        try {
            var first = executor.submit(() -> {
                start.await();
                return registerProduction.execute(command(
                    formula.getId(),
                    "5",
                    List.of(new ProductionSourceAllocationCommand(firstSource.getId(), new BigDecimal("5"))),
                    ProductionLotCodeMode.GENERATED,
                    null
                ));
            });
            var second = executor.submit(() -> {
                start.await();
                return registerProduction.execute(command(
                    formula.getId(),
                    "5",
                    List.of(new ProductionSourceAllocationCommand(secondSource.getId(), new BigDecimal("5"))),
                    ProductionLotCodeMode.GENERATED,
                    null
                ));
            });

            start.countDown();
            var firstResult = first.get(10, TimeUnit.SECONDS);
            var secondResult = second.get(10, TimeUnit.SECONDS);

            assertThat(Set.of(firstResult.lotCode(), secondResult.lotCode()))
                .containsExactlyInAnyOrder("CNC-000-001-09-2026", "CNC-000-002-09-2026");
            assertThat(firstResult.outputBatchId()).isNotEqualTo(secondResult.outputBatchId());
            assertThat(balance(firstSource.getId())).isEqualByComparingTo("0");
            assertThat(balance(secondSource.getId())).isEqualByComparingTo("0");
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private InventoryItem item(Category category, String productionTypeCode) {
        return inventoryItemRepository.save(InventoryItem.create(
            "Production item " + UUID.randomUUID(),
            null,
            category,
            UnitOfMeasure.MILLILITER,
            null,
            productionTypeCode
        ));
    }

    private ProductionFormula formula(UUID outputItemId, UUID ingredientItemId, String outputQty, String ingredientQty) {
        return productionFormulaRepository.save(ProductionFormula.create(
            outputItemId,
            new BigDecimal(outputQty),
            UnitOfMeasure.MILLILITER,
            List.of(new FormulaIngredient(
                ingredientItemId,
                new BigDecimal(ingredientQty),
                UnitOfMeasure.MILLILITER
            ))
        ));
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

    private BigDecimal balance(UUID batchId) {
        return batchRepository.findById(batchId).orElseThrow().getCurrentQuantity();
    }

    private int count(String sql, UUID id) {
        return jdbcTemplate.queryForObject(sql, Integer.class, id);
    }

    private static RegisterProductionCommand command(
        UUID formulaId,
        String outputQuantity,
        List<ProductionSourceAllocationCommand> allocations,
        ProductionLotCodeMode mode,
        String manualLotCode
    ) {
        return new RegisterProductionCommand(
            formulaId,
            new BigDecimal(outputQuantity),
            allocations,
            LocalDate.of(2026, 9, 3),
            LocalDate.of(2026, 9, 3),
            null,
            mode,
            manualLotCode
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock productionRegistrationClock() {
            return Clock.fixed(
                Instant.parse("2026-09-03T12:00:00Z"),
                ZoneId.of("America/Sao_Paulo")
            );
        }
    }
}
