package com.ceudelavanda.lavandaflow.production.application.genealogy;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItem;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItemRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.Batch;
import com.ceudelavanda.lavandaflow.inventory.domain.BatchRepository;
import com.ceudelavanda.lavandaflow.production.application.execution.ProductionSourceAllocationCommand;
import com.ceudelavanda.lavandaflow.production.application.execution.RegisterProduction;
import com.ceudelavanda.lavandaflow.production.application.execution.RegisterProductionCommand;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import({
    TestcontainersConfiguration.class,
    GetBatchGenealogyIntegrationTest.FixedClockConfiguration.class
})
class GetBatchGenealogyIntegrationTest {

    @Autowired private GetBatchGenealogy getBatchGenealogy;
    @Autowired private ProductionGenealogyQueryRepository genealogyRepository;
    @Autowired private RegisterProduction registerProduction;
    @Autowired private InventoryItemRepository inventoryItemRepository;
    @Autowired private BatchRepository batchRepository;
    @Autowired private ProductionFormulaRepository productionFormulaRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void shouldResolveMultiLevelUpstreamGenealogyWithMultipleExternalSourceBatches() {
        var rawA = item("Genealogy raw A", Category.ESSENCE, null);
        var rawB = item("Genealogy raw B", Category.OTHER, null);
        var intermediateItem = item("Genealogy intermediate", Category.OTHER, "GIN");
        var finalItem = item("Genealogy final", Category.OTHER, "GFN");

        var intermediateFormula = formula(
            intermediateItem.getId(),
            "10",
            List.of(
                ingredient(rawA.getId(), "4"),
                ingredient(rawB.getId(), "6")
            )
        );
        var finalFormula = formula(
            finalItem.getId(),
            "10",
            List.of(ingredient(intermediateItem.getId(), "10"))
        );

        var rawBatchA = batch(rawA.getId(), "RAW-A", "4");
        var rawBatchB = batch(rawB.getId(), "RAW-B", "6");
        var intermediate = register(
            intermediateFormula.getId(),
            "10",
            List.of(
                allocation(rawBatchA.getId(), "4"),
                allocation(rawBatchB.getId(), "6")
            ),
            "GENEALOGY-INTERMEDIATE"
        );
        var finalExecution = register(
            finalFormula.getId(),
            "10",
            List.of(allocation(intermediate.outputBatchId(), "10")),
            "GENEALOGY-FINAL"
        );

        var result = getBatchGenealogy.execute(finalExecution.outputBatchId(), GenealogyDirection.UPSTREAM);

        assertThat(result.rootBatch().origin()).isEqualTo(GenealogyBatchOrigin.INTERNALLY_PRODUCED);
        assertThat(result.rootBatch().lotCode()).isEqualTo("GENEALOGY-FINAL");
        assertThat(result.downstream()).isEmpty();
        assertThat(result.upstream()).singleElement().satisfies(finalEdge -> {
            assertThat(finalEdge.consumedQuantity()).isEqualByComparingTo("10");
            assertThat(finalEdge.sourceBatch().batchId()).isEqualTo(intermediate.outputBatchId());
            assertThat(finalEdge.sourceBatch().origin()).isEqualTo(GenealogyBatchOrigin.INTERNALLY_PRODUCED);
            assertThat(finalEdge.next()).hasSize(2);
            assertThat(finalEdge.next())
                .extracting(edge -> edge.consumedQuantity().stripTrailingZeros())
                .containsExactlyInAnyOrder(new BigDecimal("4"), new BigDecimal("6"));
            assertThat(finalEdge.next())
                .allSatisfy(rawEdge -> {
                    assertThat(rawEdge.sourceBatch().origin())
                        .isEqualTo(GenealogyBatchOrigin.EXTERNAL_OR_NON_PRODUCED);
                    assertThat(rawEdge.next()).isEmpty();
                });
            assertThat(finalEdge.next())
                .extracting(edge -> edge.sourceBatch().lotCode())
                .containsExactlyInAnyOrder("RAW-A", "RAW-B");
        });
    }

    @Test
    void shouldResolveBranchingDownstreamGenealogyAtArbitraryDepth() {
        var rawItem = item("Branching raw", Category.ESSENCE, null);
        var firstOutputItem = item("Branch output A", Category.OTHER, "BRA");
        var secondOutputItem = item("Branch output B", Category.OTHER, "BRB");
        var descendantItem = item("Branch descendant", Category.OTHER, "BRC");

        var firstFormula = formula(firstOutputItem.getId(), "5", List.of(ingredient(rawItem.getId(), "5")));
        var secondFormula = formula(secondOutputItem.getId(), "5", List.of(ingredient(rawItem.getId(), "5")));
        var descendantFormula = formula(descendantItem.getId(), "5", List.of(ingredient(firstOutputItem.getId(), "5")));
        var sharedRawBatch = batch(rawItem.getId(), "BRANCH-ROOT", "10");

        var first = register(
            firstFormula.getId(),
            "5",
            List.of(allocation(sharedRawBatch.getId(), "5")),
            "BRANCH-A"
        );
        var second = register(
            secondFormula.getId(),
            "5",
            List.of(allocation(sharedRawBatch.getId(), "5")),
            "BRANCH-B"
        );
        var descendant = register(
            descendantFormula.getId(),
            "5",
            List.of(allocation(first.outputBatchId(), "5")),
            "BRANCH-C"
        );

        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM production_consumption WHERE source_batch_id = ?",
            Integer.class,
            sharedRawBatch.getId()
        )).isEqualTo(2);

        var flatDownstream = genealogyRepository.findDownstreamEdges(sharedRawBatch.getId());
        assertThat(flatDownstream).hasSize(3);
        assertThat(flatDownstream)
            .extracting(ProductionGenealogyEdgeRecord::outputBatchId)
            .containsExactlyInAnyOrder(first.outputBatchId(), second.outputBatchId(), descendant.outputBatchId());

        var result = getBatchGenealogy.execute(sharedRawBatch.getId(), GenealogyDirection.DOWNSTREAM);

        assertThat(result.rootBatch().origin()).isEqualTo(GenealogyBatchOrigin.EXTERNAL_OR_NON_PRODUCED);
        assertThat(result.upstream()).isEmpty();
        assertThat(result.downstream()).hasSize(2);
        assertThat(result.downstream())
            .extracting(edge -> edge.outputBatch().batchId())
            .containsExactlyInAnyOrder(first.outputBatchId(), second.outputBatchId());

        var firstBranch = result.downstream().stream()
            .filter(edge -> edge.outputBatch().batchId().equals(first.outputBatchId()))
            .findFirst()
            .orElseThrow();
        assertThat(firstBranch.consumedQuantity()).isEqualByComparingTo("5");
        assertThat(firstBranch.next()).singleElement().satisfies(edge -> {
            assertThat(edge.sourceBatch().batchId()).isEqualTo(first.outputBatchId());
            assertThat(edge.outputBatch().batchId()).isEqualTo(descendant.outputBatchId());
            assertThat(edge.next()).isEmpty();
        });

        var secondBranch = result.downstream().stream()
            .filter(edge -> edge.outputBatch().batchId().equals(second.outputBatchId()))
            .findFirst()
            .orElseThrow();
        assertThat(secondBranch.next()).isEmpty();
    }

    @Test
    void shouldPreserveStableBatchNotFoundContract() {
        var missingBatchId = UUID.randomUUID();

        assertThatThrownBy(() -> getBatchGenealogy.execute(missingBatchId, GenealogyDirection.BOTH))
            .isInstanceOf(BatchGenealogyNotFoundException.class)
            .satisfies(exception -> assertThat(((BatchGenealogyNotFoundException) exception).getCode())
                .isEqualTo("BATCH_NOT_FOUND"));
    }

    private InventoryItem item(String name, Category category, String productionTypeCode) {
        return inventoryItemRepository.save(InventoryItem.create(
            name + " " + UUID.randomUUID(),
            null,
            category,
            UnitOfMeasure.MILLILITER,
            null,
            productionTypeCode
        ));
    }

    private ProductionFormula formula(
        UUID outputItemId,
        String outputQuantity,
        List<FormulaIngredient> ingredients
    ) {
        return productionFormulaRepository.save(ProductionFormula.create(
            outputItemId,
            new BigDecimal(outputQuantity),
            UnitOfMeasure.MILLILITER,
            ingredients
        ));
    }

    private FormulaIngredient ingredient(UUID itemId, String quantity) {
        return new FormulaIngredient(itemId, new BigDecimal(quantity), UnitOfMeasure.MILLILITER);
    }

    private Batch batch(UUID itemId, String lotCode, String quantity) {
        return batchRepository.save(Batch.create(
            itemId,
            null,
            lotCode,
            new BigDecimal(quantity),
            LocalDate.of(2026, 9, 1),
            null
        ));
    }

    private ProductionSourceAllocationCommand allocation(UUID batchId, String quantity) {
        return new ProductionSourceAllocationCommand(batchId, new BigDecimal(quantity));
    }

    private com.ceudelavanda.lavandaflow.production.application.execution.RegisterProductionResult register(
        UUID formulaId,
        String outputQuantity,
        List<ProductionSourceAllocationCommand> allocations,
        String manualLotCode
    ) {
        return registerProduction.execute(new RegisterProductionCommand(
            formulaId,
            new BigDecimal(outputQuantity),
            allocations,
            LocalDate.of(2026, 9, 3),
            LocalDate.of(2026, 9, 3),
            null,
            ProductionLotCodeMode.MANUAL,
            manualLotCode
        ));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock genealogyClock() {
            return Clock.fixed(
                Instant.parse("2026-09-03T12:00:00Z"),
                ZoneId.of("America/Sao_Paulo")
            );
        }
    }
}
