package com.ceudelavanda.lavandaflow.acceptance;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import com.ceudelavanda.lavandaflow.catalog.InventoryItemLookup;
import com.ceudelavanda.lavandaflow.catalog.InventoryItemSnapshot;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.catalog.application.RegisterInventoryItem;
import com.ceudelavanda.lavandaflow.catalog.application.RegisterInventoryItemCommand;
import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import com.ceudelavanda.lavandaflow.inventory.application.alerts.ExpirationAlertStatus;
import com.ceudelavanda.lavandaflow.inventory.application.alerts.GetExpirationAlerts;
import com.ceudelavanda.lavandaflow.inventory.application.alerts.GetExpirationAlertsQuery;
import com.ceudelavanda.lavandaflow.inventory.application.alerts.GetLowStockAlerts;
import com.ceudelavanda.lavandaflow.inventory.application.batch.GetBatchInventory;
import com.ceudelavanda.lavandaflow.inventory.application.dashboard.GetOperationalDashboard;
import com.ceudelavanda.lavandaflow.inventory.application.fefo.RegisterFefoWithdrawal;
import com.ceudelavanda.lavandaflow.inventory.application.fefo.RegisterFefoWithdrawalCommand;
import com.ceudelavanda.lavandaflow.inventory.application.history.GetMovementHistory;
import com.ceudelavanda.lavandaflow.inventory.application.history.GetMovementHistoryQuery;
import com.ceudelavanda.lavandaflow.inventory.application.initialsnapshot.ImportInitialInventorySnapshot;
import com.ceudelavanda.lavandaflow.inventory.application.initialsnapshot.InitialInventoryImportMode;
import com.ceudelavanda.lavandaflow.inventory.application.minimumstock.ConfigureMinimumStockLevel;
import com.ceudelavanda.lavandaflow.inventory.application.receipt.RegisterStockReceipt;
import com.ceudelavanda.lavandaflow.inventory.application.receipt.RegisterStockReceiptCommand;
import com.ceudelavanda.lavandaflow.inventory.application.stock.GetCurrentStock;
import com.ceudelavanda.lavandaflow.inventory.application.stock.GetCurrentStockQuery;
import com.ceudelavanda.lavandaflow.inventory.domain.MovementType;
import com.ceudelavanda.lavandaflow.production.application.execution.ProductionSourceAllocationCommand;
import com.ceudelavanda.lavandaflow.production.application.execution.RegisterProduction;
import com.ceudelavanda.lavandaflow.production.application.execution.RegisterProductionCommand;
import com.ceudelavanda.lavandaflow.production.application.formula.CreateProductionFormula;
import com.ceudelavanda.lavandaflow.production.application.formula.ProductionFormulaDefinitionCommand;
import com.ceudelavanda.lavandaflow.production.application.formula.ProductionFormulaIngredientCommand;
import com.ceudelavanda.lavandaflow.production.application.genealogy.GenealogyBatchOrigin;
import com.ceudelavanda.lavandaflow.production.application.genealogy.GenealogyDirection;
import com.ceudelavanda.lavandaflow.production.application.genealogy.GetBatchGenealogy;
import com.ceudelavanda.lavandaflow.production.domain.ProductionLotCodeMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import({
    TestcontainersConfiguration.class,
    V1OperationalReadinessAcceptanceTest.FixedClockConfiguration.class
})
class V1OperationalReadinessAcceptanceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 30);
    private static final LocalDate IMPORT_EFFECTIVE_DATE = LocalDate.of(2026, 9, 1);
    private static final int EXPIRATION_WINDOW_DAYS = 31;

    @Autowired private ImportInitialInventorySnapshot importer;
    @Autowired private InventoryItemLookup inventoryItemLookup;
    @Autowired private RegisterInventoryItem registerInventoryItem;
    @Autowired private RegisterStockReceipt registerStockReceipt;
    @Autowired private RegisterFefoWithdrawal registerFefoWithdrawal;
    @Autowired private GetCurrentStock getCurrentStock;
    @Autowired private GetBatchInventory getBatchInventory;
    @Autowired private GetMovementHistory getMovementHistory;
    @Autowired private ConfigureMinimumStockLevel configureMinimumStockLevel;
    @Autowired private GetOperationalDashboard getOperationalDashboard;
    @Autowired private GetLowStockAlerts getLowStockAlerts;
    @Autowired private GetExpirationAlerts getExpirationAlerts;
    @Autowired private CreateProductionFormula createProductionFormula;
    @Autowired private RegisterProduction registerProduction;
    @Autowired private GetBatchGenealogy getBatchGenealogy;
    @Autowired private JdbcTemplate jdbcTemplate;

    @TempDir
    Path directory;

    @BeforeEach
    void clearOperationalState() {
        jdbcTemplate.execute("""
            TRUNCATE TABLE
                production_consumption, production_execution, production_formula_ingredient,
                production_formula, production_lot_sequence, stock_movement, inventory_batch,
                inventory_minimum_stock_level, inventory_item
            CASCADE
            """);
    }

    @Test
    void shouldComposeInitialSnapshotInventoryProductionDashboardAlertsAndGenealogy() throws IOException {
        var snapshot = syntheticSnapshot();

        var dryRun = importer.execute(InitialInventoryImportMode.DRY_RUN, snapshot, IMPORT_EFFECTIVE_DATE);
        assertThat(dryRun.totalRowCount()).isEqualTo(3);
        assertThat(dryRun.openingStockCount()).isEqualTo(2);
        assertThat(dryRun.catalogOnlyCount()).isEqualTo(1);
        assertDatabaseCounts(0, 0, 0);

        var apply = importer.execute(InitialInventoryImportMode.APPLY, snapshot, IMPORT_EFFECTIVE_DATE);
        assertThat(apply.rows()).isEqualTo(dryRun.rows());
        assertThat(apply.rejectedCount()).isZero();
        assertDatabaseCounts(3, 2, 2);

        Files.delete(snapshot);
        assertThat(snapshot).doesNotExist();

        var sourceItem = itemNamed("Operational source");
        var expiredItem = itemNamed("Expired snapshot");
        var zeroStockItem = itemNamed("Catalog only");
        var sourceOpeningBatch = getBatchInventory.execute(sourceItem.id()).batches().getFirst();
        var openingMovement = movementHistory(sourceItem.id()).getFirst();

        assertThat(sourceOpeningBatch.currentQuantity()).isEqualByComparingTo("20.500000");
        assertThat(sourceOpeningBatch.expiresAt()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(openingMovement.type()).isEqualTo(MovementType.ENTRY);
        assertThat(openingMovement.quantity()).isEqualByComparingTo("20.500000");
        assertThat(getBatchInventory.execute(zeroStockItem.id()).batches()).isEmpty();
        assertThat(movementHistory(zeroStockItem.id())).isEmpty();

        var receipt = registerStockReceipt.execute(new RegisterStockReceiptCommand(
            sourceItem.id(),
            null,
            "ACCEPTANCE-RECEIPT",
            new BigDecimal("5.000000"),
            TODAY.minusDays(1),
            TODAY.plusDays(61),
            "Entrada da aceitação V1"
        ));
        var withdrawal = registerFefoWithdrawal.execute(new RegisterFefoWithdrawalCommand(
            sourceItem.id(),
            new BigDecimal("3.000000"),
            "Consumo FEFO da aceitação V1"
        ));

        assertThat(withdrawal.allocations()).singleElement().satisfies(allocation -> {
            assertThat(allocation.batchId()).isEqualTo(sourceOpeningBatch.batchId());
            assertThat(allocation.quantity()).isEqualByComparingTo("3.000000");
        });
        assertThat(currentStock(sourceItem.id())).isEqualByComparingTo("22.500000");

        configureMinimumStockLevel.execute(sourceItem.id(), new BigDecimal("19.000000"));
        var intermediateItem = registerItem("Acceptance intermediate", "BAS");
        var finalItem = registerItem("Acceptance final", "BDS");
        var intermediateFormula = createFormula(
            intermediateItem.id(), "10.000000", sourceItem.id(), "4.250000"
        );
        var finalFormula = createFormula(
            finalItem.id(), "6.000000", intermediateItem.id(), "6.000000"
        );

        var productionA = registerProduction.execute(new RegisterProductionCommand(
            intermediateFormula.id(),
            new BigDecimal("10.000000"),
            List.of(new ProductionSourceAllocationCommand(
                sourceOpeningBatch.batchId(), new BigDecimal("4.250000")
            )),
            TODAY,
            TODAY,
            null,
            ProductionLotCodeMode.GENERATED,
            null
        ));

        assertThat(productionA.lotCode()).matches("BAS-000-[0-9]{3}-09-2026");
        assertThat(productionA.consumptions()).singleElement().satisfies(consumption -> {
            assertThat(consumption.sourceBatchId()).isEqualTo(sourceOpeningBatch.batchId());
            assertThat(consumption.quantity()).isEqualByComparingTo("4.250000");
        });
        assertThat(batchQuantity(sourceItem.id(), sourceOpeningBatch.batchId()))
            .isEqualByComparingTo("13.250000");
        assertThat(currentStock(sourceItem.id())).isEqualByComparingTo("18.250000");
        assertSingleOutputBatch(intermediateItem.id(), productionA.outputBatchId(), "10.000000");

        var productionB = registerProduction.execute(new RegisterProductionCommand(
            finalFormula.id(),
            new BigDecimal("6.000000"),
            List.of(new ProductionSourceAllocationCommand(
                productionA.outputBatchId(), new BigDecimal("6.000000")
            )),
            TODAY,
            TODAY,
            null,
            ProductionLotCodeMode.GENERATED,
            null
        ));

        assertThat(productionB.lotCode()).matches("BDS-000-[0-9]{3}-09-2026");
        assertThat(productionB.outputBatchId()).isNotEqualTo(productionA.outputBatchId());
        assertThat(batchQuantity(intermediateItem.id(), productionA.outputBatchId()))
            .isEqualByComparingTo("4.000000");
        assertSingleOutputBatch(finalItem.id(), productionB.outputBatchId(), "6.000000");

        var sourceHistory = movementHistory(sourceItem.id());
        assertThat(sourceHistory).hasSize(4);
        assertThat(sourceHistory).extracting(entry -> entry.movementId())
            .containsExactlyInAnyOrder(
                openingMovement.movementId(),
                receipt.movementId(),
                withdrawal.allocations().getFirst().movementId(),
                productionA.consumptions().getFirst().movementId()
            );
        assertThat(sourceHistory).filteredOn(entry ->
            entry.movementId().equals(productionA.consumptions().getFirst().movementId())
        ).singleElement().satisfies(entry -> {
            assertThat(entry.type()).isEqualTo(MovementType.CONSUMPTION);
            assertThat(entry.quantity()).isEqualByComparingTo("4.250000");
        });
        assertThat(movementHistory(intermediateItem.id()))
            .extracting(entry -> entry.type())
            .containsExactlyInAnyOrder(MovementType.ENTRY, MovementType.CONSUMPTION);
        assertThat(movementHistory(finalItem.id()))
            .singleElement().extracting(entry -> entry.type()).isEqualTo(MovementType.ENTRY);

        var lowStock = getLowStockAlerts.execute();
        var expiration = getExpirationAlerts.execute(new GetExpirationAlertsQuery(EXPIRATION_WINDOW_DAYS));
        var dashboard = getOperationalDashboard.execute(EXPIRATION_WINDOW_DAYS);
        assertThat(lowStock.alerts()).singleElement().satisfies(alert ->
            assertThat(alert.inventoryItemId()).isEqualTo(sourceItem.id())
        );
        assertThat(expiration.alerts()).extracting(alert -> alert.batchId())
            .containsExactlyInAnyOrder(
                sourceOpeningBatch.batchId(),
                getBatchInventory.execute(expiredItem.id()).batches().getFirst().batchId()
            );
        assertThat(expiration.alerts()).extracting(alert -> alert.status())
            .containsExactlyInAnyOrder(
                ExpirationAlertStatus.EXPIRING_SOON,
                ExpirationAlertStatus.EXPIRED
            );
        assertThat(dashboard.asOfDate()).isEqualTo(TODAY);
        assertThat(dashboard.activeItemCount()).isEqualTo(5);
        assertThat(dashboard.lowStockItemCount()).isEqualTo(1);
        assertThat(dashboard.outOfStockItemCount()).isEqualTo(2);
        assertThat(dashboard.expiringSoonBatchCount()).isEqualTo(1);
        assertThat(dashboard.expiredBatchCount()).isEqualTo(1);

        var upstream = getBatchGenealogy.execute(
            productionB.outputBatchId(), GenealogyDirection.UPSTREAM
        );
        assertThat(upstream.rootBatch().batchId()).isEqualTo(productionB.outputBatchId());
        assertThat(upstream.upstream()).singleElement().satisfies(productionBEdge -> {
            assertThat(productionBEdge.executionId()).isEqualTo(productionB.executionId());
            assertThat(productionBEdge.sourceBatch().batchId()).isEqualTo(productionA.outputBatchId());
            assertThat(productionBEdge.consumedQuantity()).isEqualByComparingTo("6.000000");
            assertThat(productionBEdge.next()).singleElement().satisfies(productionAEdge -> {
                assertThat(productionAEdge.executionId()).isEqualTo(productionA.executionId());
                assertThat(productionAEdge.sourceBatch().batchId()).isEqualTo(sourceOpeningBatch.batchId());
                assertThat(productionAEdge.consumedQuantity()).isEqualByComparingTo("4.250000");
                assertThat(productionAEdge.sourceBatch().origin())
                    .isEqualTo(GenealogyBatchOrigin.EXTERNAL_OR_NON_PRODUCED);
            });
        });

        var downstream = getBatchGenealogy.execute(
            sourceOpeningBatch.batchId(), GenealogyDirection.DOWNSTREAM
        );
        assertThat(downstream.downstream()).singleElement().satisfies(productionAEdge -> {
            assertThat(productionAEdge.outputBatch().batchId()).isEqualTo(productionA.outputBatchId());
            assertThat(productionAEdge.next()).singleElement().satisfies(productionBEdge ->
                assertThat(productionBEdge.outputBatch().batchId()).isEqualTo(productionB.outputBatchId())
            );
        });

        assertThat(Stream.of(sourceItem.id(), expiredItem.id(), intermediateItem.id(), finalItem.id())
            .flatMap(itemId -> getBatchInventory.execute(itemId).batches().stream()))
            .allMatch(batch -> batch.currentQuantity().signum() >= 0);
    }

    private Path syntheticSnapshot() throws IOException {
        var file = directory.resolve("v1-acceptance.csv");
        Files.writeString(file, """
            Nome do Perfume,Genero,Ml Disponiveis,Expired,Retirada,EssenceReference,ProductionTypeCode,LotCode
            Operational source,F,20.500000,10/26,,014,PFM,PFM-014-001-09-2026
            Expired snapshot,M,2.000000,09/26,,015,PFM,PFM-015-001-09-2026
            Catalog only,C,0,,,016,PFM,
            """, StandardCharsets.UTF_8);
        return file;
    }

    private InventoryItemSnapshot itemNamed(String name) {
        return inventoryItemLookup.findAllActive().stream()
            .filter(item -> item.name().equals(name))
            .findFirst()
            .orElseThrow();
    }

    private com.ceudelavanda.lavandaflow.catalog.application.InventoryItemResult registerItem(
        String name,
        String productionTypeCode
    ) {
        return registerInventoryItem.execute(new RegisterInventoryItemCommand(
            name,
            "V1 operational readiness acceptance fixture",
            Category.OTHER,
            UnitOfMeasure.MILLILITER,
            null,
            productionTypeCode
        ));
    }

    private com.ceudelavanda.lavandaflow.production.application.formula.ProductionFormulaResult createFormula(
        UUID outputItemId,
        String outputQuantity,
        UUID ingredientItemId,
        String ingredientQuantity
    ) {
        return createProductionFormula.execute(new ProductionFormulaDefinitionCommand(
            outputItemId,
            new BigDecimal(outputQuantity),
            List.of(new ProductionFormulaIngredientCommand(
                ingredientItemId, new BigDecimal(ingredientQuantity)
            ))
        ));
    }

    private BigDecimal currentStock(UUID inventoryItemId) {
        return getCurrentStock.execute(new GetCurrentStockQuery(inventoryItemId, true))
            .totalCurrentQuantity();
    }

    private BigDecimal batchQuantity(UUID inventoryItemId, UUID batchId) {
        return getBatchInventory.execute(inventoryItemId).batches().stream()
            .filter(batch -> batch.batchId().equals(batchId))
            .findFirst()
            .orElseThrow()
            .currentQuantity();
    }

    private List<com.ceudelavanda.lavandaflow.inventory.application.history.MovementHistoryEntryResult> movementHistory(
        UUID inventoryItemId
    ) {
        return getMovementHistory.execute(new GetMovementHistoryQuery(
            inventoryItemId, null, null, null, null, 0, 100
        )).content();
    }

    private void assertSingleOutputBatch(UUID inventoryItemId, UUID batchId, String quantity) {
        assertThat(getBatchInventory.execute(inventoryItemId).batches())
            .singleElement()
            .satisfies(batch -> {
                assertThat(batch.batchId()).isEqualTo(batchId);
                assertThat(batch.initialQuantity()).isEqualByComparingTo(quantity);
                assertThat(batch.currentQuantity()).isEqualByComparingTo(quantity);
            });
        assertThat(movementHistory(inventoryItemId))
            .singleElement()
            .satisfies(movement -> {
                assertThat(movement.batchId()).isEqualTo(batchId);
                assertThat(movement.type()).isEqualTo(MovementType.ENTRY);
                assertThat(movement.quantity()).isEqualByComparingTo(quantity);
            });
    }

    private void assertDatabaseCounts(int items, int batches, int movements) {
        assertThat(count("inventory_item")).isEqualTo(items);
        assertThat(count("inventory_batch")).isEqualTo(batches);
        assertThat(count("stock_movement")).isEqualTo(movements);
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock operationalReadinessClock() {
            return Clock.fixed(
                Instant.parse("2026-09-30T15:00:00Z"),
                ZoneId.of("America/Sao_Paulo")
            );
        }
    }
}
