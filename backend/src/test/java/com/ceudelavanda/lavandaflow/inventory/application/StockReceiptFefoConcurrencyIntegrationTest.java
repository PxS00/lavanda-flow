package com.ceudelavanda.lavandaflow.inventory.application;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.catalog.application.RegisterInventoryItem;
import com.ceudelavanda.lavandaflow.catalog.application.RegisterInventoryItemCommand;
import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import com.ceudelavanda.lavandaflow.inventory.application.fefo.RegisterFefoWithdrawal;
import com.ceudelavanda.lavandaflow.inventory.application.fefo.RegisterFefoWithdrawalCommand;
import com.ceudelavanda.lavandaflow.inventory.application.receipt.RegisterStockReceipt;
import com.ceudelavanda.lavandaflow.inventory.application.receipt.RegisterStockReceiptCommand;
import com.ceudelavanda.lavandaflow.inventory.domain.BatchRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.MovementType;
import com.ceudelavanda.lavandaflow.inventory.domain.StockMovementRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import({
    TestcontainersConfiguration.class,
    StockReceiptFefoConcurrencyIntegrationTest.ConcurrencyTestConfiguration.class
})
class StockReceiptFefoConcurrencyIntegrationTest {

    @Autowired private RegisterInventoryItem registerInventoryItem;
    @Autowired private RegisterStockReceipt registerStockReceipt;
    @Autowired private RegisterFefoWithdrawal registerFefoWithdrawal;
    @Autowired private BatchRepository batchRepository;
    @Autowired private StockMovementRepository stockMovementRepository;

    @Autowired
    @Qualifier("receiptFefoConcurrencyClock")
    private ControlledClock controlledClock;

    @Test
    void shouldMakeFefoWaitForReceiptAndAllocateFromFreshCommittedBatchSet() throws Exception {
        var item = createInventoryItem();
        var executor = newExecutor();
        controlledClock.blockNextInstant();

        try {
            var receipt = executor.submit(() -> registerStockReceipt.execute(new RegisterStockReceiptCommand(
                item.id(),
                null,
                "LOT-78-RECEIPT-FIRST",
                new BigDecimal("7.000000"),
                LocalDate.of(2026, 8, 27),
                LocalDate.of(2026, 9, 1),
                "Concurrent receipt first"
            )));

            assertThat(controlledClock.awaitBlocked(5, TimeUnit.SECONDS)).isTrue();

            var fefoStarted = new CountDownLatch(1);
            var fefo = executor.submit(() -> {
                fefoStarted.countDown();
                return registerFefoWithdrawal.execute(new RegisterFefoWithdrawalCommand(
                    item.id(),
                    new BigDecimal("7.000000"),
                    "FEFO after receipt"
                ));
            });

            assertCompetingOperationWaits(fefo, fefoStarted);
            controlledClock.releaseBlockedInstant();

            var receiptResult = receipt.get(5, TimeUnit.SECONDS);
            var fefoResult = fefo.get(5, TimeUnit.SECONDS);

            assertThat(fefoResult.allocations()).singleElement().satisfies(allocation -> {
                assertThat(allocation.batchId()).isEqualTo(receiptResult.batchId());
                assertThat(allocation.quantity()).isEqualByComparingTo("7.000000");
            });
            assertThat(currentBalance(receiptResult.batchId())).isEqualByComparingTo("0.000000");
            assertThat(stockMovementRepository.findByBatchIdOrderByOccurredAtAsc(receiptResult.batchId()))
                .hasSize(2)
                .extracting(movement -> movement.type())
                .containsExactlyInAnyOrder(MovementType.ENTRY, MovementType.CONSUMPTION);
        } finally {
            controlledClock.releaseBlockedInstant();
            shutdown(executor);
        }
    }

    @Test
    void shouldMakeReceiptWaitForFefoAndInsertOnlyAfterFefoCommits() throws Exception {
        var item = createInventoryItem();
        var existingReceipt = registerStockReceipt.execute(new RegisterStockReceiptCommand(
            item.id(),
            null,
            "LOT-78-FEFO-FIRST-EXISTING",
            new BigDecimal("5.000000"),
            LocalDate.of(2026, 8, 20),
            LocalDate.of(2026, 9, 1),
            "Existing stock"
        ));
        var executor = newExecutor();
        controlledClock.blockNextInstant();

        try {
            var fefo = executor.submit(() -> registerFefoWithdrawal.execute(new RegisterFefoWithdrawalCommand(
                item.id(),
                new BigDecimal("5.000000"),
                "FEFO first"
            )));

            assertThat(controlledClock.awaitBlocked(5, TimeUnit.SECONDS)).isTrue();

            var receiptStarted = new CountDownLatch(1);
            var receipt = executor.submit(() -> {
                receiptStarted.countDown();
                return registerStockReceipt.execute(new RegisterStockReceiptCommand(
                    item.id(),
                    null,
                    "LOT-78-FEFO-FIRST-NEW",
                    new BigDecimal("10.000000"),
                    LocalDate.of(2026, 8, 27),
                    LocalDate.of(2026, 9, 2),
                    "Receipt after FEFO"
                ));
            });

            assertCompetingOperationWaits(receipt, receiptStarted);
            controlledClock.releaseBlockedInstant();

            var fefoResult = fefo.get(5, TimeUnit.SECONDS);
            var newReceipt = receipt.get(5, TimeUnit.SECONDS);

            assertThat(fefoResult.allocations()).singleElement().satisfies(allocation -> {
                assertThat(allocation.batchId()).isEqualTo(existingReceipt.batchId());
                assertThat(allocation.quantity()).isEqualByComparingTo("5.000000");
            });
            assertThat(currentBalance(existingReceipt.batchId())).isEqualByComparingTo("0.000000");
            assertThat(currentBalance(newReceipt.batchId())).isEqualByComparingTo("10.000000");

            assertThat(stockMovementRepository.findByBatchIdOrderByOccurredAtAsc(existingReceipt.batchId()))
                .hasSize(2)
                .extracting(movement -> movement.type())
                .containsExactlyInAnyOrder(MovementType.ENTRY, MovementType.CONSUMPTION);
            assertThat(stockMovementRepository.findByBatchIdOrderByOccurredAtAsc(newReceipt.batchId()))
                .singleElement()
                .satisfies(movement -> {
                    assertThat(movement.type()).isEqualTo(MovementType.ENTRY);
                    assertThat(movement.quantity()).isEqualByComparingTo("10.000000");
                });
        } finally {
            controlledClock.releaseBlockedInstant();
            shutdown(executor);
        }
    }

    @Test
    void shouldKeepDifferentInventoryItemsIndependent() throws Exception {
        var lockedItem = createInventoryItem();
        var independentItem = createInventoryItem();
        var executor = newExecutor();
        controlledClock.blockNextInstant();

        try {
            var blockedReceipt = executor.submit(() -> registerStockReceipt.execute(new RegisterStockReceiptCommand(
                lockedItem.id(),
                null,
                "LOT-78-LOCKED-ITEM",
                new BigDecimal("4.000000"),
                LocalDate.of(2026, 8, 27),
                LocalDate.of(2026, 9, 1),
                "Blocked item receipt"
            )));

            assertThat(controlledClock.awaitBlocked(5, TimeUnit.SECONDS)).isTrue();

            var independentReceipt = executor.submit(() -> registerStockReceipt.execute(new RegisterStockReceiptCommand(
                independentItem.id(),
                null,
                "LOT-78-INDEPENDENT-ITEM",
                new BigDecimal("3.000000"),
                LocalDate.of(2026, 8, 27),
                LocalDate.of(2026, 9, 1),
                "Independent item receipt"
            )));

            var independentResult = independentReceipt.get(2, TimeUnit.SECONDS);
            assertThat(currentBalance(independentResult.batchId())).isEqualByComparingTo("3.000000");
            assertThat(blockedReceipt.isDone()).isFalse();

            controlledClock.releaseBlockedInstant();
            var blockedResult = blockedReceipt.get(5, TimeUnit.SECONDS);
            assertThat(currentBalance(blockedResult.batchId())).isEqualByComparingTo("4.000000");
        } finally {
            controlledClock.releaseBlockedInstant();
            shutdown(executor);
        }
    }

    private com.ceudelavanda.lavandaflow.catalog.application.InventoryItemResult createInventoryItem() {
        return registerInventoryItem.execute(new RegisterInventoryItemCommand(
            "Concurrent receipt/FEFO item " + UUID.randomUUID(),
            "Issue 78 concurrency fixture",
            Category.ESSENCE,
            UnitOfMeasure.MILLILITER
        ));
    }

    private BigDecimal currentBalance(UUID batchId) {
        return batchRepository.findById(batchId)
            .orElseThrow()
            .getCurrentQuantity();
    }

    private static ExecutorService newExecutor() {
        return Executors.newFixedThreadPool(2);
    }

    private static void assertCompetingOperationWaits(
        Future<?> competingOperation,
        CountDownLatch started
    ) throws InterruptedException {
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> competingOperation.get(500, TimeUnit.MILLISECONDS))
            .isInstanceOf(TimeoutException.class);
    }

    private static void shutdown(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConcurrencyTestConfiguration {

        @Bean("receiptFefoConcurrencyClock")
        @Primary
        ControlledClock controlledClock() {
            return new ControlledClock();
        }
    }

    static final class ControlledClock extends Clock {

        private static final Instant FIXED_INSTANT = Instant.parse("2026-08-27T15:00:00Z");
        private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Sao_Paulo");

        private final AtomicBoolean blockNext = new AtomicBoolean();
        private volatile CountDownLatch blocked = new CountDownLatch(0);
        private volatile CountDownLatch release = new CountDownLatch(0);

        void blockNextInstant() {
            blocked = new CountDownLatch(1);
            release = new CountDownLatch(1);
            blockNext.set(true);
        }

        boolean awaitBlocked(long timeout, TimeUnit unit) throws InterruptedException {
            return blocked.await(timeout, unit);
        }

        void releaseBlockedInstant() {
            release.countDown();
        }

        @Override
        public ZoneId getZone() {
            return BUSINESS_ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (BUSINESS_ZONE.equals(zone)) {
                return this;
            }
            return Clock.fixed(FIXED_INSTANT, zone);
        }

        @Override
        public Instant instant() {
            if (blockNext.compareAndSet(true, false)) {
                blocked.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to release controlled clock");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Controlled clock was interrupted", exception);
                }
            }

            return FIXED_INSTANT;
        }
    }
}
