package com.ceudelavanda.lavandaflow.production.application.execution;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItem;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItemRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.Batch;
import com.ceudelavanda.lavandaflow.inventory.domain.BatchRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.StockMovementRepository;
import com.ceudelavanda.lavandaflow.production.domain.FormulaIngredient;
import com.ceudelavanda.lavandaflow.production.domain.ProductionExecutionRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@Import({
    TestcontainersConfiguration.class,
    RegisterProductionRollbackIntegrationTest.FixedClockConfiguration.class
})
class RegisterProductionRollbackIntegrationTest {

    @Autowired private RegisterProduction registerProduction;
    @Autowired private InventoryItemRepository inventoryItemRepository;
    @Autowired private BatchRepository batchRepository;
    @Autowired private StockMovementRepository stockMovementRepository;
    @Autowired private ProductionFormulaRepository productionFormulaRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ProductionExecutionRepository productionExecutionRepository;

    @Test
    void shouldRollbackLotAllocationInventoryEffectsAndProductionStateWhenFinalPersistenceFails() {
        when(productionExecutionRepository.save(any())).thenThrow(new IllegalStateException("forced persistence failure"));
        var outputItem = inventoryItemRepository.save(InventoryItem.create(
            "Rollback output " + UUID.randomUUID(),
            null,
            Category.OTHER,
            UnitOfMeasure.MILLILITER,
            null,
            "RBK"
        ));
        var ingredientItem = inventoryItemRepository.save(InventoryItem.create(
            "Rollback ingredient " + UUID.randomUUID(),
            null,
            Category.ESSENCE,
            UnitOfMeasure.MILLILITER,
            "998",
            null
        ));
        var formula = productionFormulaRepository.save(ProductionFormula.create(
            outputItem.getId(),
            new BigDecimal("5"),
            UnitOfMeasure.MILLILITER,
            List.of(new FormulaIngredient(
                ingredientItem.getId(),
                new BigDecimal("5"),
                UnitOfMeasure.MILLILITER
            ))
        ));
        var source = batchRepository.save(Batch.create(
            ingredientItem.getId(),
            null,
            "ROLLBACK-SOURCE",
            new BigDecimal("5"),
            LocalDate.of(2026, 9, 1),
            null
        ));

        assertThatThrownBy(() -> registerProduction.execute(new RegisterProductionCommand(
            formula.getId(),
            new BigDecimal("5"),
            List.of(new ProductionSourceAllocationCommand(source.getId(), new BigDecimal("5"))),
            LocalDate.of(2026, 9, 3),
            LocalDate.of(2026, 9, 3),
            null,
            ProductionLotCodeMode.GENERATED,
            null
        ))).isInstanceOf(IllegalStateException.class);

        assertThat(batchRepository.findById(source.getId()).orElseThrow().getCurrentQuantity())
            .isEqualByComparingTo("5");
        assertThat(stockMovementRepository.findByBatchIdOrderByOccurredAtAsc(source.getId())).isEmpty();
        assertThat(batchRepository.findByInventoryItemId(outputItem.getId())).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM production_execution WHERE formula_id = ?",
            Integer.class,
            formula.getId()
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
            """
            SELECT count(*) FROM production_lot_sequence
            WHERE production_type_code = 'RBK'
              AND essence_reference = '000'
              AND production_year = 2026
              AND production_month = 9
            """,
            Integer.class
        )).isZero();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock productionRegistrationRollbackClock() {
            return Clock.fixed(
                Instant.parse("2026-09-03T12:00:00Z"),
                ZoneId.of("America/Sao_Paulo")
            );
        }
    }
}
