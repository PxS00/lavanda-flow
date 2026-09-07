package com.ceudelavanda.lavandaflow.inventory.application.initialsnapshot;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import com.ceudelavanda.lavandaflow.catalog.InventoryItemLookup;
import com.ceudelavanda.lavandaflow.catalog.InventoryItemRegistration;
import com.ceudelavanda.lavandaflow.inventory.application.batch.GetBatchInventory;
import com.ceudelavanda.lavandaflow.inventory.application.history.GetMovementHistory;
import com.ceudelavanda.lavandaflow.inventory.application.history.GetMovementHistoryQuery;
import com.ceudelavanda.lavandaflow.inventory.application.stock.GetCurrentStock;
import com.ceudelavanda.lavandaflow.inventory.application.stock.GetCurrentStockQuery;
import com.ceudelavanda.lavandaflow.inventory.domain.MovementType;
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
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import({
    TestcontainersConfiguration.class,
    ImportInitialInventorySnapshotIntegrationTest.FixedClockConfiguration.class
})
class ImportInitialInventorySnapshotIntegrationTest {

    private static final LocalDate EFFECTIVE_DATE = LocalDate.of(2024, 1, 10);

    @Autowired private ImportInitialInventorySnapshot importer;
    @Autowired private InventoryItemLookup inventoryItemLookup;
    @Autowired private InventoryItemRegistration inventoryItemRegistration;
    @Autowired private GetCurrentStock getCurrentStock;
    @Autowired private GetBatchInventory getBatchInventory;
    @Autowired private GetMovementHistory getMovementHistory;
    @Autowired private JdbcTemplate jdbcTemplate;

    @TempDir
    Path directory;

    @BeforeEach
    void clearInventory() {
        jdbcTemplate.update("DELETE FROM stock_movement");
        jdbcTemplate.update("DELETE FROM inventory_batch");
        jdbcTemplate.update("DELETE FROM inventory_minimum_stock_level");
        jdbcTemplate.update("DELETE FROM inventory_item");
    }

    @Test
    void shouldDryRunCompleteValidationWithoutWrites() throws IOException {
        var report = importer.execute(InitialInventoryImportMode.DRY_RUN, validSnapshot(), EFFECTIVE_DATE);

        assertThat(report.totalRowCount()).isEqualTo(3);
        assertThat(report.openingStockCount()).isEqualTo(2);
        assertThat(report.catalogOnlyCount()).isEqualTo(1);
        assertDatabaseCounts(0, 0, 0);
    }

    @Test
    void shouldApplyCatalogBatchesMovementsAndExistingReadModelsAtomically() throws IOException {
        var report = importer.execute(InitialInventoryImportMode.APPLY, validSnapshot(), EFFECTIVE_DATE);

        assertThat(report.rejectedCount()).isZero();
        assertDatabaseCounts(3, 2, 2);
        assertThat(inventoryItemLookup.findAllActive()).extracting(item -> item.name())
            .containsExactlyInAnyOrder("Scandall (M)", "Scandall (F)", "Lavender");

        var scandall = inventoryItemLookup.findAllActive().stream()
            .filter(item -> item.name().equals("Scandall (M)"))
            .findFirst()
            .orElseThrow();
        var currentStock = getCurrentStock.execute(new GetCurrentStockQuery(scandall.id(), true));
        var batches = getBatchInventory.execute(scandall.id()).batches();
        var history = getMovementHistory.execute(new GetMovementHistoryQuery(
            scandall.id(), null, null, null, null, 0, 100
        )).content();

        assertThat(currentStock.totalCurrentQuantity()).isEqualByComparingTo("10.500001");
        assertThat(batches).singleElement().satisfies(batch -> {
            assertThat(batch.initialQuantity()).isEqualByComparingTo("10.500001");
            assertThat(batch.currentQuantity()).isEqualByComparingTo("10.500001");
            assertThat(batch.receivedAt()).isEqualTo(EFFECTIVE_DATE);
            assertThat(batch.expiresAt()).isEqualTo(LocalDate.of(2024, 2, 29));
            assertThat(batch.supplierId()).isNull();
            assertThat(batch.lotCode()).isNull();
        });
        assertThat(history).singleElement().satisfies(movement -> {
            assertThat(movement.type()).isEqualTo(MovementType.ENTRY);
            assertThat(movement.quantity()).isEqualByComparingTo("10.500001");
            assertThat(movement.reason()).isEqualTo("Importação inicial do estoque");
        });

        var zeroStock = inventoryItemLookup.findAllActive().stream()
            .filter(item -> item.name().equals("Scandall (F)"))
            .findFirst()
            .orElseThrow();
        assertThat(getBatchInventory.execute(zeroStock.id()).batches()).isEmpty();
        assertThat(getMovementHistory.execute(new GetMovementHistoryQuery(
            zeroStock.id(), null, null, null, null, 0, 100
        )).content()).isEmpty();
    }

    @Test
    void shouldApplyAdjustedFinalSnapshotWithoutFabricatingWithdrawalHistory() throws IOException {
        var snapshot = write("""
            Nome do Perfume,Genero,Ml Disponiveis,Expired,Retirada
            Adjusted,F,80,02/24,-50ml
            Catalog only,M,50,,- 50ml
            Unchanged,C,3,03/24,
            """);

        var dryRun = importer.execute(InitialInventoryImportMode.DRY_RUN, snapshot, EFFECTIVE_DATE);

        assertThat(dryRun.rejectedCount()).isZero();
        assertThat(dryRun.openingStockCount()).isEqualTo(2);
        assertThat(dryRun.catalogOnlyCount()).isEqualTo(1);
        assertThat(dryRun.rows()).extracting(InitialInventoryImportRowResult::quantity)
            .containsExactly(
                new BigDecimal("30.000000"),
                new BigDecimal("0.000000"),
                new BigDecimal("3.000000")
            );
        assertDatabaseCounts(0, 0, 0);

        importer.execute(InitialInventoryImportMode.APPLY, snapshot, EFFECTIVE_DATE);

        assertDatabaseCounts(3, 2, 2);
        var adjusted = inventoryItemLookup.findAllActive().stream()
            .filter(item -> item.name().equals("Adjusted"))
            .findFirst()
            .orElseThrow();
        assertThat(getCurrentStock.execute(new GetCurrentStockQuery(adjusted.id(), true)).totalCurrentQuantity())
            .isEqualByComparingTo("30.000000");
        assertThat(getBatchInventory.execute(adjusted.id()).batches()).singleElement().satisfies(batch -> {
            assertThat(batch.initialQuantity()).isEqualByComparingTo("30.000000");
            assertThat(batch.currentQuantity()).isEqualByComparingTo("30.000000");
        });
        assertThat(getMovementHistory.execute(new GetMovementHistoryQuery(
            adjusted.id(), null, null, null, null, 0, 100
        )).content()).singleElement().satisfies(movement -> {
            assertThat(movement.type()).isEqualTo(MovementType.ENTRY);
            assertThat(movement.quantity()).isEqualByComparingTo("30.000000");
        });
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM stock_movement WHERE movement_type = 'CONSUMPTION'", Integer.class
        )).isZero();

        var catalogOnly = inventoryItemLookup.findAllActive().stream()
            .filter(item -> item.name().equals("Catalog only"))
            .findFirst()
            .orElseThrow();
        assertThat(getBatchInventory.execute(catalogOnly.id()).batches()).isEmpty();
        assertThat(getMovementHistory.execute(new GetMovementHistoryQuery(
            catalogOnly.id(), null, null, null, null, 0, 100
        )).content()).isEmpty();
    }

    @Test
    void shouldRejectInvalidFileAndInitializedCatalogBeforeSnapshotWrites() throws IOException {
        var invalid = write("""
            Nome do Perfume,Genero,Ml Disponiveis,Expired
            Invalid,F,1,
            """);

        assertThatThrownBy(() -> importer.execute(
            InitialInventoryImportMode.APPLY, invalid, EFFECTIVE_DATE
        ))
            .isInstanceOf(InitialInventoryImportException.class)
            .satisfies(exception -> assertThat(((InitialInventoryImportException) exception).report().rejectedCount())
                .isEqualTo(1));
        assertDatabaseCounts(0, 0, 0);

        var negativeAdjusted = write("""
            Nome do Perfume,Genero,Ml Disponiveis,Expired,Retirada
            Invalid adjustment,F,1,02/24,-2ml
            """);
        assertThatThrownBy(() -> importer.execute(
            InitialInventoryImportMode.APPLY, negativeAdjusted, EFFECTIVE_DATE
        ))
            .isInstanceOf(InitialInventoryImportException.class)
            .satisfies(exception -> assertThat(
                ((InitialInventoryImportException) exception).report().rows().getFirst().validationCode()
            ).isEqualTo(InitialInventoryImportValidationCode.NEGATIVE_ADJUSTED_QUANTITY));
        assertDatabaseCounts(0, 0, 0);

        var existing = inventoryItemRegistration.registerEssence("Existing inactive guard fixture");
        jdbcTemplate.update("UPDATE inventory_item SET active = false WHERE id = ?", existing.id());
        assertThatThrownBy(() -> importer.execute(
            InitialInventoryImportMode.APPLY, validSnapshot(), EFFECTIVE_DATE
        ))
            .isInstanceOf(InitialInventoryImportException.class)
            .hasMessage("Operational catalog is already initialized");
        assertDatabaseCounts(1, 0, 0);
    }

    private Path validSnapshot() throws IOException {
        return write("""
            Nome do Perfume,Genero,Ml Disponiveis,Expired
            Scandall,M,10.500001,02/24
            Scandall,F,0,
              Lavender  , F / C,3,03/24
            """);
    }

    private Path write(String csv) throws IOException {
        var file = directory.resolve("snapshot-" + System.nanoTime() + ".csv");
        Files.writeString(file, csv, StandardCharsets.UTF_8);
        return file;
    }

    private void assertDatabaseCounts(int items, int batches, int movements) {
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM inventory_item", Integer.class))
            .isEqualTo(items);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM inventory_batch", Integer.class))
            .isEqualTo(batches);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM stock_movement", Integer.class))
            .isEqualTo(movements);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock initialInventoryImportClock() {
            return Clock.fixed(Instant.parse("2024-01-15T12:00:00Z"), ZoneOffset.UTC);
        }
    }
}
