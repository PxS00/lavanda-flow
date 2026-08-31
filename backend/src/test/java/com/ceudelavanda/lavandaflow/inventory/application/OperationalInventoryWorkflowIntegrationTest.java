package com.ceudelavanda.lavandaflow.inventory.application;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.catalog.application.RegisterInventoryItem;
import com.ceudelavanda.lavandaflow.catalog.application.RegisterInventoryItemCommand;
import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import com.ceudelavanda.lavandaflow.inventory.application.batch.BatchOperationalStatus;
import com.ceudelavanda.lavandaflow.inventory.application.batch.GetBatchInventory;
import com.ceudelavanda.lavandaflow.inventory.application.fefo.RegisterFefoWithdrawal;
import com.ceudelavanda.lavandaflow.inventory.application.fefo.RegisterFefoWithdrawalCommand;
import com.ceudelavanda.lavandaflow.inventory.application.history.GetMovementHistory;
import com.ceudelavanda.lavandaflow.inventory.application.history.GetMovementHistoryQuery;
import com.ceudelavanda.lavandaflow.inventory.application.history.MovementHistoryEntryResult;
import com.ceudelavanda.lavandaflow.inventory.application.overview.GetInventoryItemOverview;
import com.ceudelavanda.lavandaflow.inventory.application.overview.GetInventoryItemOverviewQuery;
import com.ceudelavanda.lavandaflow.inventory.application.receipt.RegisterStockReceipt;
import com.ceudelavanda.lavandaflow.inventory.application.receipt.RegisterStockReceiptCommand;
import com.ceudelavanda.lavandaflow.inventory.application.stock.GetCurrentStock;
import com.ceudelavanda.lavandaflow.inventory.application.stock.GetCurrentStockQuery;
import com.ceudelavanda.lavandaflow.inventory.domain.BatchRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.MovementType;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InsufficientEligibleStockException;
import com.ceudelavanda.lavandaflow.suppliers.application.RegisterSupplier;
import com.ceudelavanda.lavandaflow.suppliers.application.RegisterSupplierCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

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
@Import({TestcontainersConfiguration.class, OperationalInventoryWorkflowIntegrationTest.FixedClockConfiguration.class})
class OperationalInventoryWorkflowIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 31);
    private static final int EXPIRATION_WINDOW_DAYS = 30;

    @Autowired private RegisterInventoryItem registerInventoryItem;
    @Autowired private RegisterSupplier registerSupplier;
    @Autowired private RegisterStockReceipt registerStockReceipt;
    @Autowired private RegisterFefoWithdrawal registerFefoWithdrawal;
    @Autowired private GetBatchInventory getBatchInventory;
    @Autowired private GetCurrentStock getCurrentStock;
    @Autowired private GetMovementHistory getMovementHistory;
    @Autowired private GetInventoryItemOverview getInventoryItemOverview;
    @Autowired private BatchRepository batchRepository;

    @Test
    void shouldExecuteOperationalWorkflowFromCatalogRegistrationThroughFefoAndReadModels() {
        var item = registerInventoryItem("Workflow happy path");
        var supplier = registerSupplier("Workflow happy path");
        var firstExpiration = TODAY.plusDays(10);
        var secondExpiration = TODAY.plusDays(20);

        var firstReceipt = registerStockReceipt.execute(new RegisterStockReceiptCommand(
            item.id(), supplier.id(), "WF-LOT-A", new BigDecimal("10.000000"),
            TODAY.minusDays(5), firstExpiration, "Workflow receipt A"
        ));
        var secondReceipt = registerStockReceipt.execute(new RegisterStockReceiptCommand(
            item.id(), supplier.id(), "WF-LOT-B", new BigDecimal("20.000000"),
            TODAY.minusDays(4), secondExpiration, "Workflow receipt B"
        ));

        var batchesBeforeWithdrawal = getBatchInventory.execute(item.id()).batches();
        assertThat(batchesBeforeWithdrawal).hasSize(2);
        assertThat(batchesBeforeWithdrawal.get(0).batchId()).isEqualTo(firstReceipt.batchId());
        assertThat(batchesBeforeWithdrawal.get(0).currentQuantity()).isEqualByComparingTo("10.000000");
        assertThat(batchesBeforeWithdrawal.get(0).status()).isEqualTo(BatchOperationalStatus.AVAILABLE);
        assertThat(batchesBeforeWithdrawal.get(1).batchId()).isEqualTo(secondReceipt.batchId());
        assertThat(batchesBeforeWithdrawal.get(1).currentQuantity()).isEqualByComparingTo("20.000000");
        assertThat(batchesBeforeWithdrawal.get(1).status()).isEqualTo(BatchOperationalStatus.AVAILABLE);

        var withdrawal = registerFefoWithdrawal.execute(new RegisterFefoWithdrawalCommand(
            item.id(), new BigDecimal("15.000000"), "Workflow FEFO consumption"
        ));

        assertThat(withdrawal.requestedQuantity()).isEqualByComparingTo("15.000000");
        assertThat(withdrawal.allocatedQuantity()).isEqualByComparingTo("15.000000");
        assertThat(withdrawal.allocations()).hasSize(2);
        assertThat(withdrawal.allocations().get(0).batchId()).isEqualTo(firstReceipt.batchId());
        assertThat(withdrawal.allocations().get(0).quantity()).isEqualByComparingTo("10.000000");
        assertThat(withdrawal.allocations().get(1).batchId()).isEqualTo(secondReceipt.batchId());
        assertThat(withdrawal.allocations().get(1).quantity()).isEqualByComparingTo("5.000000");

        var persistedFirstBatch = batchRepository.findById(firstReceipt.batchId()).orElseThrow();
        var persistedSecondBatch = batchRepository.findById(secondReceipt.batchId()).orElseThrow();
        assertThat(persistedFirstBatch.getCurrentQuantity()).isEqualByComparingTo("0.000000");
        assertThat(persistedSecondBatch.getCurrentQuantity()).isEqualByComparingTo("15.000000");

        var currentStock = getCurrentStock.execute(new GetCurrentStockQuery(item.id(), true));
        assertThat(currentStock.totalCurrentQuantity()).isEqualByComparingTo("15.000000");
        assertThat(currentStock.totalCurrentQuantity()).isEqualByComparingTo(
            persistedFirstBatch.getCurrentQuantity().add(persistedSecondBatch.getCurrentQuantity())
        );

        var batchesAfterWithdrawal = getBatchInventory.execute(item.id()).batches();
        assertThat(batchesAfterWithdrawal).hasSize(2);
        assertThat(findBatchQuantity(batchesAfterWithdrawal, firstReceipt.batchId())).isEqualByComparingTo("0.000000");
        assertThat(findBatchQuantity(batchesAfterWithdrawal, secondReceipt.batchId())).isEqualByComparingTo("15.000000");

        var history = movementHistory(item.id());
        assertThat(history).hasSize(4);
        assertThat(history).extracting(MovementHistoryEntryResult::movementId)
            .containsExactlyInAnyOrder(
                firstReceipt.movementId(),
                secondReceipt.movementId(),
                withdrawal.allocations().get(0).movementId(),
                withdrawal.allocations().get(1).movementId()
            );
        assertMovement(history, firstReceipt.batchId(), MovementType.ENTRY, "10.000000");
        assertMovement(history, secondReceipt.batchId(), MovementType.ENTRY, "20.000000");
        assertMovement(history, firstReceipt.batchId(), MovementType.CONSUMPTION, "10.000000");
        assertMovement(history, secondReceipt.batchId(), MovementType.CONSUMPTION, "5.000000");

        var overview = getInventoryItemOverview.execute(new GetInventoryItemOverviewQuery(
            item.id(), EXPIRATION_WINDOW_DAYS
        ));
        assertThat(overview.totalCurrentQuantity()).isEqualByComparingTo("15.000000");
        assertThat(overview.availableQuantity()).isEqualByComparingTo("15.000000");
        assertThat(overview.nonZeroBatchCount()).isEqualTo(1);
        assertThat(overview.nearestExpiration()).isEqualTo(secondExpiration);
        assertThat(overview.expiredBatchCount()).isZero();
        assertThat(overview.expiringSoonBatchCount()).isEqualTo(1);
        assertThat(overview.outOfStock()).isFalse();
    }

    @Test
    void shouldLeaveReceiptStateUntouchedWhenFefoCannotSatisfyEligibleQuantity() {
        var item = registerInventoryItem("Workflow rollback");
        var supplier = registerSupplier("Workflow rollback");

        var availableReceipt = registerStockReceipt.execute(new RegisterStockReceiptCommand(
            item.id(), supplier.id(), "WF-AVAILABLE", new BigDecimal("5.000000"),
            TODAY.minusDays(10), TODAY.plusDays(7), "Available workflow receipt"
        ));
        var expiredReceipt = registerStockReceipt.execute(new RegisterStockReceiptCommand(
            item.id(), supplier.id(), "WF-EXPIRED", new BigDecimal("5.000000"),
            TODAY.minusDays(20), TODAY.minusDays(1), "Expired workflow receipt"
        ));

        assertThatThrownBy(() -> registerFefoWithdrawal.execute(new RegisterFefoWithdrawalCommand(
            item.id(), new BigDecimal("7.000000"), "Should rollback"
        )))
            .isInstanceOf(InsufficientEligibleStockException.class);

        var availableBatch = batchRepository.findById(availableReceipt.batchId()).orElseThrow();
        var expiredBatch = batchRepository.findById(expiredReceipt.batchId()).orElseThrow();
        assertThat(availableBatch.getCurrentQuantity()).isEqualByComparingTo("5.000000");
        assertThat(expiredBatch.getCurrentQuantity()).isEqualByComparingTo("5.000000");

        var currentStock = getCurrentStock.execute(new GetCurrentStockQuery(item.id(), true));
        assertThat(currentStock.totalCurrentQuantity()).isEqualByComparingTo("10.000000");

        var operationalBatches = getBatchInventory.execute(item.id()).batches();
        assertThat(findBatchQuantity(operationalBatches, availableReceipt.batchId())).isEqualByComparingTo("5.000000");
        assertThat(findBatchQuantity(operationalBatches, expiredReceipt.batchId())).isEqualByComparingTo("5.000000");
        assertThat(findBatchStatus(operationalBatches, availableReceipt.batchId())).isEqualTo(BatchOperationalStatus.AVAILABLE);
        assertThat(findBatchStatus(operationalBatches, expiredReceipt.batchId())).isEqualTo(BatchOperationalStatus.EXPIRED);

        var history = movementHistory(item.id());
        assertThat(history).hasSize(2);
        assertThat(history).allMatch(entry -> entry.type() == MovementType.ENTRY);
        assertThat(history).noneMatch(entry -> entry.type() == MovementType.CONSUMPTION);

        var overview = getInventoryItemOverview.execute(new GetInventoryItemOverviewQuery(
            item.id(), EXPIRATION_WINDOW_DAYS
        ));
        assertThat(overview.totalCurrentQuantity()).isEqualByComparingTo("10.000000");
        assertThat(overview.availableQuantity()).isEqualByComparingTo("5.000000");
        assertThat(overview.nonZeroBatchCount()).isEqualTo(2);
        assertThat(overview.expiredBatchCount()).isEqualTo(1);
        assertThat(overview.nearestExpiration()).isEqualTo(TODAY.plusDays(7));
    }

    private com.ceudelavanda.lavandaflow.catalog.application.InventoryItemResult registerInventoryItem(String prefix) {
        return registerInventoryItem.execute(new RegisterInventoryItemCommand(
            prefix + " " + UUID.randomUUID(),
            "Operational workflow integration fixture",
            Category.ESSENCE,
            UnitOfMeasure.MILLILITER
        ));
    }

    private com.ceudelavanda.lavandaflow.suppliers.application.SupplierResult registerSupplier(String prefix) {
        return registerSupplier.execute(new RegisterSupplierCommand(
            prefix + " supplier " + UUID.randomUUID(),
            null,
            "workflow@example.test",
            "Operational workflow integration fixture"
        ));
    }

    private List<MovementHistoryEntryResult> movementHistory(UUID inventoryItemId) {
        return getMovementHistory.execute(new GetMovementHistoryQuery(
            inventoryItemId, null, null, null, null, 0, 100
        )).content();
    }

    private static BigDecimal findBatchQuantity(
        List<com.ceudelavanda.lavandaflow.inventory.application.batch.BatchInventoryEntryResult> batches,
        UUID batchId
    ) {
        return batches.stream()
            .filter(batch -> batch.batchId().equals(batchId))
            .findFirst()
            .orElseThrow()
            .currentQuantity();
    }

    private static BatchOperationalStatus findBatchStatus(
        List<com.ceudelavanda.lavandaflow.inventory.application.batch.BatchInventoryEntryResult> batches,
        UUID batchId
    ) {
        return batches.stream()
            .filter(batch -> batch.batchId().equals(batchId))
            .findFirst()
            .orElseThrow()
            .status();
    }

    private static void assertMovement(
        List<MovementHistoryEntryResult> history,
        UUID batchId,
        MovementType type,
        String quantity
    ) {
        assertThat(history)
            .filteredOn(entry -> entry.batchId().equals(batchId) && entry.type() == type)
            .singleElement()
            .satisfies(entry -> assertThat(entry.quantity()).isEqualByComparingTo(quantity));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock operationalWorkflowClock() {
            return Clock.fixed(
                Instant.parse("2026-08-31T15:00:00Z"),
                ZoneId.of("America/Sao_Paulo")
            );
        }
    }
}
