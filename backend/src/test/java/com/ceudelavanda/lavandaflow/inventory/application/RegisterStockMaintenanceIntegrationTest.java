package com.ceudelavanda.lavandaflow.inventory.application;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItem;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItemRepository;
import com.ceudelavanda.lavandaflow.inventory.application.movement.RegisterExpiredStockDisposal;
import com.ceudelavanda.lavandaflow.inventory.application.movement.RegisterExpiredStockDisposalCommand;
import com.ceudelavanda.lavandaflow.inventory.application.movement.RegisterStockLoss;
import com.ceudelavanda.lavandaflow.inventory.application.movement.RegisterStockLossCommand;
import com.ceudelavanda.lavandaflow.inventory.domain.Batch;
import com.ceudelavanda.lavandaflow.inventory.domain.BatchRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.MovementType;
import com.ceudelavanda.lavandaflow.inventory.domain.StockMovementRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.BatchNotExpiredException;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InsufficientStockException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RegisterStockMaintenanceIntegrationTest {

    @Autowired
    private RegisterStockLoss registerStockLoss;

    @Autowired
    private RegisterExpiredStockDisposal registerExpiredStockDisposal;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private Clock clock;

    @Test
    void shouldPersistExactLossAndItsImmutableMovement() {
        var batch = saveBatch("20.000001", null);

        var result = registerStockLoss.execute(new RegisterStockLossCommand(
            batch.getId(), new BigDecimal("12.500001"), "Bottle broke during handling"
        ));

        assertThat(result.resultingBalance()).isEqualByComparingTo("7.500000");
        assertThat(batchRepository.findById(batch.getId())).get()
            .satisfies(found -> assertThat(found.getCurrentQuantity()).isEqualByComparingTo("7.500000"));
        assertThat(stockMovementRepository.findByBatchIdOrderByOccurredAtAsc(batch.getId()))
            .singleElement()
            .satisfies(movement -> {
                assertThat(movement.type()).isEqualTo(MovementType.LOSS);
                assertThat(movement.quantity()).isEqualByComparingTo("12.500001");
                assertThat(movement.reason()).isEqualTo("Bottle broke during handling");
            });
    }

    @Test
    void shouldPersistExpiredDisposalWithExactZeroBalance() {
        var today = LocalDate.now(clock);
        var batch = saveBatch("12.500000", today.minusDays(1));

        var result = registerExpiredStockDisposal.execute(new RegisterExpiredStockDisposalCommand(
            batch.getId(), new BigDecimal("12.500000"), "Expired stock physically discarded"
        ));

        assertThat(result.resultingBalance()).isEqualByComparingTo("0");
        assertThat(batchRepository.findById(batch.getId())).get()
            .satisfies(found -> assertThat(found.getCurrentQuantity()).isEqualByComparingTo("0"));
        assertThat(stockMovementRepository.findByBatchIdOrderByOccurredAtAsc(batch.getId()))
            .singleElement()
            .satisfies(movement -> assertThat(movement.type()).isEqualTo(MovementType.EXPIRED_DISPOSAL));
    }

    @Test
    void shouldAllowDisposalWhenExpirationIsToday() {
        var batch = saveBatch("2.000000", LocalDate.now(clock));

        registerExpiredStockDisposal.execute(new RegisterExpiredStockDisposalCommand(
            batch.getId(), BigDecimal.ONE, "Expired stock physically discarded"
        ));

        assertThat(batchRepository.findById(batch.getId())).get()
            .satisfies(found -> assertThat(found.getCurrentQuantity()).isEqualByComparingTo("1"));
    }

    @Test
    void shouldLeaveBalanceAndHistoryUnchangedWhenStockIsInsufficient() {
        var batch = saveBatch("1.000000", null);

        assertThatThrownBy(() -> registerStockLoss.execute(new RegisterStockLossCommand(
            batch.getId(), new BigDecimal("1.000001"), "Damaged"
        ))).isInstanceOf(InsufficientStockException.class);

        assertUnchanged(batch, "1.000000");
    }

    @Test
    void shouldLeaveBalanceAndHistoryUnchangedWhenDisposalBatchIsNotExpired() {
        var today = LocalDate.now(clock);
        var futureBatch = saveBatch("1.000000", today.plusDays(1));
        var noExpirationBatch = saveBatch("1.000000", null);

        assertThatThrownBy(() -> registerExpiredStockDisposal.execute(new RegisterExpiredStockDisposalCommand(
            futureBatch.getId(), BigDecimal.ONE, "Discarded"
        ))).isInstanceOf(BatchNotExpiredException.class);
        assertThatThrownBy(() -> registerExpiredStockDisposal.execute(new RegisterExpiredStockDisposalCommand(
            noExpirationBatch.getId(), BigDecimal.ONE, "Discarded"
        ))).isInstanceOf(BatchNotExpiredException.class);

        assertUnchanged(futureBatch, "1.000000");
        assertUnchanged(noExpirationBatch, "1.000000");
    }

    private Batch saveBatch(String quantity, LocalDate expiresAt) {
        var item = inventoryItemRepository.save(InventoryItem.create(
            "Maintenance item " + UUID.randomUUID(), "Test item", Category.OTHER, UnitOfMeasure.UNIT
        ));
        var today = LocalDate.now(clock);
        return batchRepository.save(Batch.create(
            item.getId(), null, "LOT-" + UUID.randomUUID(), new BigDecimal(quantity),
            expiresAt == null ? today : expiresAt.minusDays(1), expiresAt
        ));
    }

    private void assertUnchanged(Batch batch, String quantity) {
        assertThat(batchRepository.findById(batch.getId())).get()
            .satisfies(found -> assertThat(found.getCurrentQuantity()).isEqualByComparingTo(quantity));
        assertThat(stockMovementRepository.findByBatchIdOrderByOccurredAtAsc(batch.getId())).isEmpty();
    }
}
