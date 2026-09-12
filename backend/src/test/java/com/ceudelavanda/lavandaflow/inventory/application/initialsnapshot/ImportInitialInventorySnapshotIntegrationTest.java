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
    private static final String HEADER =
        "Nome do Perfume,Genero,Ml Disponiveis,Expired,Retirada,EssenceReference,ProductionTypeCode,LotCode";

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
        assertThat(report.rejectedCount()).isZero();
        assertDatabaseCounts(0, 0, 0);
    }

    @Test
    void shouldApplyFinishedProductsMultipleLotsAndOpeningHistoryAtomically() throws IOException {
        var report = importer.execute(InitialInventoryImportMode.APPLY, validSnapshot(), EFFECTIVE_DATE);

        assertThat(report.rejectedCount()).isZero();
        assertDatabaseCounts(2, 2, 2);
        assertThat(inventoryItemLookup.findAllActive()).extracting(item -> item.name())
            .containsExactlyInAnyOrder("Serena", "Golden Femme");

        var serena = inventoryItemLookup.findAllActive().stream()
            .filter(item -> item.name().equals("Serena"))
            .findFirst()
            .orElseThrow();

        assertThat(jdbcTemplate.queryForMap(
            """
            SELECT category, default_unit, essence_reference, production_type_code, product_gender
            FROM inventory_item
            WHERE id = ?
            """,
            serena.id()
        )).containsEntry("category", "FINISHED_PRODUCT")
            .containsEntry("default_unit", "MILLILITER")
            .containsEntry("essence_reference", "014")
            .containsEntry("production_type_code", "PFM")
            .containsEntry("product_gender", "F");

        var currentStock = getCurrentStock.execute(new GetCurrentStockQuery(serena.id(), true));
        var batches = getBatchInventory.execute(serena.id()).batches();
        var history = getMovementHistory.execute(new GetMovementHistoryQuery(
            serena.id(), null, null, null, null, 0, 100
        )).content();

        assertThat(currentStock.totalCurrentQuantity()).isEqualByComparingTo("15.500001");
        assertThat(batches).hasSize(2);
        assertThat(batches).extracting(batch -> batch.lotCode())
            .containsExactlyInAnyOrder("PFM-014-001-01-2024", "PFM-014-002-01-2024");
        assertThat(batches).allSatisfy(batch -> {
            assertThat(batch.receivedAt()).isEqualTo(EFFECTIVE_DATE);
            assertThat(batch.supplierId()).isNull();
        });
        assertThat(history).hasSize(2).allSatisfy(movement -> {
            assertThat(movement.type()).isEqualTo(MovementType.ENTRY);
            assertThat(movement.reason()).isEqualTo("Importação inicial do estoque");
        });
        assertThat(history).extracting(movement -> movement.quantity())
            .usingElementComparator(BigDecimal::compareTo)
            .containsExactlyInAnyOrder(new BigDecimal("10.500001"), new BigDecimal("5.000000"));

        var catalogOnly = inventoryItemLookup.findAllActive().stream()
            .filter(item -> item.name().equals("Golden Femme"))
            .findFirst()
            .orElseThrow();
        assertThat(getBatchInventory.execute(catalogOnly.id()).batches()).isEmpty();
        assertThat(getMovementHistory.execute(new GetMovementHistoryQuery(
            catalogOnly.id(), null, null, null, null, 0, 100
        )).content()).isEmpty();
    }

    @Test
    void shouldApplyAdjustedSnapshotWithoutFabricatingWithdrawalHistory() throws IOException {
        var snapshot = write("""
            %s
            Adjusted,F,80,02/24,-50ml,014,PFM,PFM-014-001-01-2024
            Catalog only,M,50,,- 50ml,015,PFM,
            Unchanged,C,3,03/24,,016,PFM,PFM-016-001-01-2024
            """.formatted(HEADER));

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
            assertThat(batch.lotCode()).isEqualTo("PFM-014-001-01-2024");
        });
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM stock_movement WHERE movement_type = 'CONSUMPTION'", Integer.class
        )).isZero();
    }

    @Test
    void shouldAllowEqualNamesAcrossDistinctStableIdentities() throws IOException {
        var snapshot = write("""
            %s
            Shared Name,F,1,02/24,,014,PFM,PFM-014-001-01-2024
            Shared Name,F,2,03/24,,015,PFM,PFM-015-001-01-2024
            """.formatted(HEADER));

        importer.execute(InitialInventoryImportMode.APPLY, snapshot, EFFECTIVE_DATE);

        assertDatabaseCounts(2, 2, 2);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM inventory_item WHERE name = 'Shared Name'", Integer.class
        )).isEqualTo(2);
    }

    @Test
    void shouldRejectInvalidSnapshotAndInitializedCatalogBeforeWrites() throws IOException {
        var invalid = write("""
            %s
            Invalid,F,1,02/24,,014,PFM,
            """.formatted(HEADER));

        assertThatThrownBy(() -> importer.execute(
            InitialInventoryImportMode.APPLY, invalid, EFFECTIVE_DATE
        ))
            .isInstanceOf(InitialInventoryImportException.class)
            .satisfies(exception -> {
                var report = ((InitialInventoryImportException) exception).report();
                assertThat(report.rejectedCount()).isEqualTo(1);
                assertThat(report.rows().getFirst().validationCode())
                    .isEqualTo(InitialInventoryImportValidationCode.LOT_CODE_REQUIRED);
            });
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
            %s
            Serena,F,10.500001,02/24,,014,PFM,PFM-014-001-01-2024
            Serena,F,5,03/24,,014,PFM,PFM-014-002-01-2024
            Golden Femme,F,0,,,,021,PFM,
            """.formatted(HEADER));
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
