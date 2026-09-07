package com.ceudelavanda.lavandaflow.inventory.application.production;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItem;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItemRepository;
import com.ceudelavanda.lavandaflow.inventory.*;
import com.ceudelavanda.lavandaflow.inventory.domain.Batch;
import com.ceudelavanda.lavandaflow.inventory.domain.BatchRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.MovementType;
import com.ceudelavanda.lavandaflow.inventory.domain.StockMovementRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InsufficientStockException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
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
    ProductionStockApplicationIntegrationTest.ConcurrencyTestConfiguration.class
})
class ProductionStockApplicationIntegrationTest {

    @Autowired private ProductionStockApplication productionStockApplication;
    @Autowired private InventoryItemRepository inventoryItemRepository;
    @Autowired private BatchRepository batchRepository;
    @Autowired private StockMovementRepository stockMovementRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired @Qualifier("productionStockConcurrencyClock") private ControlledClock controlledClock;

    @Test
    void shouldRequireAnOuterTransaction() {
        var source = source("10", null);
        var output = item();

        assertThatThrownBy(() -> productionStockApplication.apply(command(
            List.of(new ProductionSourceAllocation(source.getId(), BigDecimal.ONE)), output.getId(), "1"
        ))).isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void shouldRollbackAllEffectsWhenOneExactSourceIsInsufficient() {
        var first = source("10", null);
        var second = source("2", null);
        var output = item();

        assertThatThrownBy(() -> apply(command(List.of(
            new ProductionSourceAllocation(first.getId(), new BigDecimal("5")),
            new ProductionSourceAllocation(second.getId(), new BigDecimal("5"))
        ), output.getId(), "5"))).isInstanceOf(InsufficientStockException.class);

        assertThat(balance(first.getId())).isEqualByComparingTo("10");
        assertThat(balance(second.getId())).isEqualByComparingTo("2");
        assertThat(stockMovementRepository.findByBatchIdOrderByOccurredAtAsc(first.getId())).isEmpty();
        assertThat(stockMovementRepository.findByBatchIdOrderByOccurredAtAsc(second.getId())).isEmpty();
        assertThat(batchRepository.findByInventoryItemId(output.getId())).isEmpty();
    }

    @Test
    void shouldRollbackAllInventoryEffectsWithItsOuterTransaction() {
        var source = source("10", null);
        var output = item();
        var transactionTemplate = new TransactionTemplate(transactionManager);

        var result = transactionTemplate.execute(status -> {
            var applied = productionStockApplication.apply(command(
                List.of(new ProductionSourceAllocation(source.getId(), new BigDecimal("4"))), output.getId(), "3"
            ));
            status.setRollbackOnly();
            return applied;
        });

        assertThat(result).isNotNull();
        assertThat(balance(source.getId())).isEqualByComparingTo("10");
        assertThat(stockMovementRepository.findByBatchIdOrderByOccurredAtAsc(source.getId())).isEmpty();
        assertThat(batchRepository.findById(result.outputBatchId())).isEmpty();
        assertThat(stockMovementRepository.findByBatchIdOrderByOccurredAtAsc(result.outputBatchId())).isEmpty();
    }

    @Test
    void shouldPreventConcurrentOverConsumption() throws Exception {
        var source = source("100", null);
        var output = item();
        var executor = Executors.newFixedThreadPool(2);
        controlledClock.blockNextInstant();

        try {
            var first = executor.submit(() -> apply(command(
                List.of(new ProductionSourceAllocation(source.getId(), new BigDecimal("70"))), output.getId(), "1"
            )));
            assertThat(controlledClock.awaitBlocked(5, TimeUnit.SECONDS)).isTrue();
            var secondStarted = new CountDownLatch(1);
            var second = executor.submit(() -> {
                secondStarted.countDown();
                return apply(command(
                    List.of(new ProductionSourceAllocation(source.getId(), new BigDecimal("70"))), output.getId(), "1"
                ));
            });
            assertCompetingOperationWaits(second, secondStarted);
            controlledClock.releaseBlockedInstant();

            var successful = 0;
            for (var future : List.of(first, second)) {
                try {
                    future.get(5, TimeUnit.SECONDS);
                    successful++;
                } catch (java.util.concurrent.ExecutionException exception) {
                    assertThat(exception.getCause()).isInstanceOf(InsufficientStockException.class);
                }
            }
            assertThat(successful).isEqualTo(1);
            assertThat(balance(source.getId())).isEqualByComparingTo("30");
            assertThat(stockMovementRepository.findByBatchIdOrderByOccurredAtAsc(source.getId()))
                .singleElement()
                .satisfies(movement -> {
                    assertThat(movement.type()).isEqualTo(MovementType.CONSUMPTION);
                    assertThat(movement.quantity()).isEqualByComparingTo("70");
                });
            assertOutputEntries(output.getId(), 1);
        } finally {
            controlledClock.releaseBlockedInstant();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void shouldApplyCompetingMultiBatchRequestsInDeterministicLockOrder() throws Exception {
        var first = source("10", null);
        var second = source("10", null);
        var output = item();
        var executor = Executors.newFixedThreadPool(2);
        controlledClock.blockNextInstant();

        try {
            var forward = executor.submit(() -> apply(command(List.of(
                new ProductionSourceAllocation(first.getId(), BigDecimal.ONE),
                new ProductionSourceAllocation(second.getId(), BigDecimal.ONE)
            ), output.getId(), "1")));
            assertThat(controlledClock.awaitBlocked(5, TimeUnit.SECONDS)).isTrue();
            var reverseStarted = new CountDownLatch(1);
            var reverse = executor.submit(() -> {
                reverseStarted.countDown();
                return apply(command(List.of(
                    new ProductionSourceAllocation(second.getId(), BigDecimal.ONE),
                    new ProductionSourceAllocation(first.getId(), BigDecimal.ONE)
                ), output.getId(), "1"));
            });
            assertCompetingOperationWaits(reverse, reverseStarted);
            controlledClock.releaseBlockedInstant();

            forward.get(5, TimeUnit.SECONDS);
            reverse.get(5, TimeUnit.SECONDS);
            assertThat(balance(first.getId())).isEqualByComparingTo("8");
            assertThat(balance(second.getId())).isEqualByComparingTo("8");
            assertThat(stockMovementRepository.findByBatchIdOrderByOccurredAtAsc(first.getId())).hasSize(2);
            assertThat(stockMovementRepository.findByBatchIdOrderByOccurredAtAsc(second.getId())).hasSize(2);
            assertOutputEntries(output.getId(), 2);
        } finally {
            controlledClock.releaseBlockedInstant();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private ProductionStockResult apply(ProductionStockCommand command) {
        return new TransactionTemplate(transactionManager).execute(status -> productionStockApplication.apply(command));
    }

    private InventoryItem item() {
        return inventoryItemRepository.save(InventoryItem.create(
            "Production stock " + UUID.randomUUID(), null, Category.OTHER, UnitOfMeasure.UNIT
        ));
    }

    private Batch source(String quantity, LocalDate expiresAt) {
        var item = item();
        return batchRepository.save(Batch.create(
            item.getId(), null, "SOURCE-" + UUID.randomUUID(), new BigDecimal(quantity), LocalDate.of(2026, 9, 1), expiresAt
        ));
    }

    private BigDecimal balance(UUID batchId) {
        return batchRepository.findById(batchId).orElseThrow().getCurrentQuantity();
    }

    private void assertOutputEntries(UUID inventoryItemId, int expectedBatchCount) {
        var outputBatches = batchRepository.findByInventoryItemId(inventoryItemId);
        assertThat(outputBatches).hasSize(expectedBatchCount).allSatisfy(batch -> {
            assertThat(batch.getSupplierId()).isNull();
            assertThat(batch.getInitialQuantity()).isEqualByComparingTo("1");
            assertThat(batch.getCurrentQuantity()).isEqualByComparingTo("1");
            assertThat(stockMovementRepository.findByBatchIdOrderByOccurredAtAsc(batch.getId()))
                .singleElement()
                .satisfies(movement -> {
                    assertThat(movement.type()).isEqualTo(MovementType.ENTRY);
                    assertThat(movement.quantity()).isEqualByComparingTo("1");
                });
        });
    }

    private static ProductionStockCommand command(
        List<ProductionSourceAllocation> sources,
        UUID outputItemId,
        String outputQuantity
    ) {
        return new ProductionStockCommand(sources, new ProductionOutputBatch(
            outputItemId, "BDS-000-001-09-2026", new BigDecimal(outputQuantity), LocalDate.of(2026, 9, 3), null
        ));
    }

    private static void assertCompetingOperationWaits(
        Future<?> competingOperation,
        CountDownLatch started
    ) throws InterruptedException {
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> competingOperation.get(500, TimeUnit.MILLISECONDS))
            .isInstanceOf(TimeoutException.class);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConcurrencyTestConfiguration {

        @Bean("productionStockConcurrencyClock")
        @Primary
        ControlledClock controlledClock() {
            return new ControlledClock();
        }
    }

    static final class ControlledClock extends Clock {

        private static final Instant FIXED_INSTANT = Instant.parse("2026-09-03T12:00:00Z");
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
            return BUSINESS_ZONE.equals(zone) ? this : Clock.fixed(FIXED_INSTANT, zone);
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
