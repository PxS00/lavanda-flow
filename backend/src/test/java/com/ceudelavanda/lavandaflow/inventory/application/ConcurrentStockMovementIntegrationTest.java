package com.ceudelavanda.lavandaflow.inventory.application;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItem;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItemRepository;
import com.ceudelavanda.lavandaflow.inventory.application.command.RegisterFefoWithdrawalCommand;
import com.ceudelavanda.lavandaflow.inventory.application.command.RegisterStockAdjustmentCommand;
import com.ceudelavanda.lavandaflow.inventory.application.command.RegisterStockEntryCommand;
import com.ceudelavanda.lavandaflow.inventory.application.command.RegisterStockWithdrawalCommand;
import com.ceudelavanda.lavandaflow.inventory.domain.Batch;
import com.ceudelavanda.lavandaflow.inventory.domain.BatchRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.MovementType;
import com.ceudelavanda.lavandaflow.inventory.domain.StockMovementRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InsufficientEligibleStockException;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InsufficientStockException;
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
    ConcurrentStockMovementIntegrationTest.ConcurrencyTestConfiguration.class
})
class ConcurrentStockMovementIntegrationTest {

    @Autowired
    private RegisterStockEntry registerStockEntry;

    @Autowired
    private RegisterStockWithdrawal registerStockWithdrawal;

    @Autowired
    private RegisterStockAdjustment registerStockAdjustment;

    @Autowired
    private RegisterFefoWithdrawal registerFefoWithdrawal;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    @Qualifier("controlledConcurrencyClock")
    private ControlledClock controlledClock;

    @Test
    void shouldSerializeCompetingWithdrawalsAndRejectStaleNegativeBalance() throws Exception {
        var item = createInventoryItem();
        var batch = createBatch(item.getId(), "10.000", null);
        var executor = newExecutor();
        controlledClock.blockNextInstant();

        try {
            var first = executor.submit(() -> registerStockWithdrawal.execute(
                new RegisterStockWithdrawalCommand(
                    batch.getId(),
                    new BigDecimal("7.000"),
                    "First concurrent withdrawal"
                )
            ));

            assertThat(controlledClock.awaitBlocked(5, TimeUnit.SECONDS)).isTrue();

            var secondStarted = new CountDownLatch(1);
            var second = executor.submit(() -> {
                secondStarted.countDown();
                return registerStockWithdrawal.execute(
                    new RegisterStockWithdrawalCommand(
                        batch.getId(),
                        new BigDecimal("7.000"),
                        "Second concurrent withdrawal"
                    )
                );
            });

            assertCompetingOperationWaits(second, secondStarted);
            controlledClock.releaseBlockedInstant();

            assertThat(first.get(5, TimeUnit.SECONDS).resultingBalance())
                .isEqualByComparingTo("3.000");
            assertThatThrownBy(() -> second.get(5, TimeUnit.SECONDS))
                .hasCauseInstanceOf(InsufficientStockException.class);

            assertThat(currentBalance(batch.getId())).isEqualByComparingTo("3.000");
            assertThat(stockMovementRepository.findByBatchIdOrderByOccurredAtAsc(batch.getId()))
                .singleElement()
                .satisfies(movement -> {
                    assertThat(movement.type()).isEqualTo(MovementType.CONSUMPTION);
                    assertThat(movement.quantity()).isEqualByComparingTo("7.000");
                });
        } finally {
            controlledClock.releaseBlockedInstant();
            shutdown(executor);
        }
    }

    @Test
    void shouldPreventLostUpdateBetweenEntryAndWithdrawal() throws Exception {
        var item = createInventoryItem();
        var batch = createBatch(item.getId(), "100.000", null);
        var executor = newExecutor();
        controlledClock.blockNextInstant();

        try {
            var entry = executor.submit(() -> registerStockEntry.execute(
                new RegisterStockEntryCommand(
                    batch.getId(),
                    new BigDecimal("50.000"),
                    "Concurrent replenishment"
                )
            ));

            assertThat(controlledClock.awaitBlocked(5, TimeUnit.SECONDS)).isTrue();

            var withdrawalStarted = new CountDownLatch(1);
            var withdrawal = executor.submit(() -> {
                withdrawalStarted.countDown();
                return registerStockWithdrawal.execute(
                    new RegisterStockWithdrawalCommand(
                        batch.getId(),
                        new BigDecimal("40.000"),
                        "Concurrent consumption"
                    )
                );
            });

            assertCompetingOperationWaits(withdrawal, withdrawalStarted);
            controlledClock.releaseBlockedInstant();

            assertThat(entry.get(5, TimeUnit.SECONDS).resultingBalance())
                .isEqualByComparingTo("150.000");
            assertThat(withdrawal.get(5, TimeUnit.SECONDS).resultingBalance())
                .isEqualByComparingTo("110.000");

            assertThat(currentBalance(batch.getId())).isEqualByComparingTo("110.000");
            assertThat(stockMovementRepository.findByBatchIdOrderByOccurredAtAsc(batch.getId()))
                .extracting(movement -> movement.type())
                .containsExactlyInAnyOrder(MovementType.ENTRY, MovementType.CONSUMPTION);
        } finally {
            controlledClock.releaseBlockedInstant();
            shutdown(executor);
        }
    }

    @Test
    void shouldSerializeNegativeAdjustmentAgainstWithdrawal() throws Exception {
        var item = createInventoryItem();
        var batch = createBatch(item.getId(), "100.000", null);
        var executor = newExecutor();
        controlledClock.blockNextInstant();

        try {
            var adjustment = executor.submit(() -> registerStockAdjustment.execute(
                new RegisterStockAdjustmentCommand(
                    batch.getId(),
                    new BigDecimal("-60.000"),
                    "Concurrent physical correction"
                )
            ));

            assertThat(controlledClock.awaitBlocked(5, TimeUnit.SECONDS)).isTrue();

            var withdrawalStarted = new CountDownLatch(1);
            var withdrawal = executor.submit(() -> {
                withdrawalStarted.countDown();
                return registerStockWithdrawal.execute(
                    new RegisterStockWithdrawalCommand(
                        batch.getId(),
                        new BigDecimal("50.000"),
                        "Concurrent consumption"
                    )
                );
            });

            assertCompetingOperationWaits(withdrawal, withdrawalStarted);
            controlledClock.releaseBlockedInstant();

            assertThat(adjustment.get(5, TimeUnit.SECONDS).resultingBalance())
                .isEqualByComparingTo("40.000");
            assertThatThrownBy(() -> withdrawal.get(5, TimeUnit.SECONDS))
                .hasCauseInstanceOf(InsufficientStockException.class);

            assertThat(currentBalance(batch.getId())).isEqualByComparingTo("40.000");
            assertThat(stockMovementRepository.findByBatchIdOrderByOccurredAtAsc(batch.getId()))
                .singleElement()
                .satisfies(movement -> {
                    assertThat(movement.type()).isEqualTo(MovementType.ADJUSTMENT_OUT);
                    assertThat(movement.quantity()).isEqualByComparingTo("60.000");
                });
        } finally {
            controlledClock.releaseBlockedInstant();
            shutdown(executor);
        }
    }

    @Test
    void shouldSerializeConcurrentFefoAcrossMultipleBatches() throws Exception {
        var item = createInventoryItem();
        var firstBatch = createBatch(item.getId(), "5.000", LocalDate.of(2026, 9, 1));
        var secondBatch = createBatch(item.getId(), "5.000", LocalDate.of(2026, 9, 2));
        var executor = newExecutor();
        controlledClock.blockNextInstant();

        try {
            var first = executor.submit(() -> registerFefoWithdrawal.execute(
                new RegisterFefoWithdrawalCommand(
                    item.getId(),
                    new BigDecimal("7.000"),
                    "First concurrent FEFO"
                )
            ));

            assertThat(controlledClock.awaitBlocked(5, TimeUnit.SECONDS)).isTrue();

            var secondStarted = new CountDownLatch(1);
            var second = executor.submit(() -> {
                secondStarted.countDown();
                return registerFefoWithdrawal.execute(
                    new RegisterFefoWithdrawalCommand(
                        item.getId(),
                        new BigDecimal("7.000"),
                        "Second concurrent FEFO"
                    )
                );
            });

            assertCompetingOperationWaits(second, secondStarted);
            controlledClock.releaseBlockedInstant();

            assertThat(first.get(5, TimeUnit.SECONDS).allocatedQuantity())
                .isEqualByComparingTo("7.000");
            assertThatThrownBy(() -> second.get(5, TimeUnit.SECONDS))
                .hasCauseInstanceOf(InsufficientEligibleStockException.class);

            var remaining = currentBalance(firstBatch.getId())
                .add(currentBalance(secondBatch.getId()));
            assertThat(remaining).isEqualByComparingTo("3.000");

            var movements = new java.util.ArrayList<>(
                stockMovementRepository.findByBatchIdOrderByOccurredAtAsc(firstBatch.getId())
            );
            movements.addAll(
                stockMovementRepository.findByBatchIdOrderByOccurredAtAsc(secondBatch.getId())
            );

            assertThat(movements).hasSize(2);
            assertThat(movements)
                .allSatisfy(movement -> assertThat(movement.type())
                    .isEqualTo(MovementType.CONSUMPTION));
            assertThat(movements.stream()
                .map(movement -> movement.quantity())
                .reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("7.000");
        } finally {
            controlledClock.releaseBlockedInstant();
            shutdown(executor);
        }
    }

    private InventoryItem createInventoryItem() {
        return inventoryItemRepository.save(
            InventoryItem.create(
                "Concurrent item " + UUID.randomUUID(),
                "Concurrency integration test",
                Category.ESSENCE,
                UnitOfMeasure.MILLILITER
            )
        );
    }

    private Batch createBatch(UUID inventoryItemId, String quantity, LocalDate expiresAt) {
        return batchRepository.save(
            Batch.create(
                inventoryItemId,
                null,
                "LOT-" + UUID.randomUUID(),
                new BigDecimal(quantity),
                LocalDate.of(2026, 8, 20),
                expiresAt
            )
        );
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

        @Bean("controlledConcurrencyClock")
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
