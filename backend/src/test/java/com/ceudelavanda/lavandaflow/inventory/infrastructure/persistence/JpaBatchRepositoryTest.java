package com.ceudelavanda.lavandaflow.inventory.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItem;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItemRepository;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.inventory.domain.Batch;
import com.ceudelavanda.lavandaflow.inventory.domain.BatchRepository;
import com.ceudelavanda.lavandaflow.suppliers.domain.Supplier;
import com.ceudelavanda.lavandaflow.suppliers.domain.SupplierRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class JpaBatchRepositoryTest {

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldSaveAndFindBatch() {
        var inventoryItem = inventoryItemRepository.save(InventoryItem.create(
            "Good Girl Essence",
            "Fragrance essence",
            Category.ESSENCE,
            UnitOfMeasure.MILLILITER
        ));
        var supplier = supplierRepository.save(Supplier.create(
            "Lavanda Inputs",
            null,
            null,
            null
        ));
        var batch = Batch.create(
            inventoryItem.getId(),
            supplier.getId(),
            "GG-2026-01",
            new BigDecimal("1500.500"),
            LocalDate.of(2026, 8, 24),
            LocalDate.of(2027, 10, 1)
        );

        batchRepository.save(batch);

        var persistedBatch = batchRepository.findById(batch.getId());

        assertThat(persistedBatch)
            .isPresent()
            .get()
            .satisfies(found -> {
                assertThat(found.getInventoryItemId()).isEqualTo(inventoryItem.getId());
                assertThat(found.getSupplierId()).isEqualTo(supplier.getId());
                assertThat(found.getLotCode()).isEqualTo("GG-2026-01");
                assertThat(found.getInitialQuantity()).isEqualByComparingTo("1500.500");
                assertThat(found.getCurrentQuantity()).isEqualByComparingTo("1500.500");
                assertThat(found.getReceivedAt()).isEqualTo(LocalDate.of(2026, 8, 24));
                assertThat(found.getExpiresAt()).isEqualTo(LocalDate.of(2027, 10, 1));
            });
    }

    @Test
    void shouldPersistBatchWithoutOptionalSupplierLotCodeOrExpirationDate() {
        var inventoryItem = inventoryItemRepository.save(InventoryItem.create(
            "Bottle 200ml",
            null,
            Category.BOTTLE,
            UnitOfMeasure.UNIT
        ));
        var batch = Batch.create(
            inventoryItem.getId(),
            null,
            null,
            new BigDecimal("100.000"),
            LocalDate.of(2026, 8, 24),
            null
        );

        batchRepository.save(batch);

        var persistedBatch = batchRepository.findById(batch.getId());

        assertThat(persistedBatch)
            .isPresent()
            .get()
            .satisfies(found -> {
                assertThat(found.getSupplierId()).isNull();
                assertThat(found.getLotCode()).isNull();
                assertThat(found.getExpiresAt()).isNull();
            });
    }

    @Test
    void shouldFindAllBatchesForInventoryItemWithCompleteDomainValues() {
        var requestedItem = inventoryItemRepository.save(InventoryItem.create(
            "Requested essence",
            null,
            Category.ESSENCE,
            UnitOfMeasure.MILLILITER
        ));
        var otherItem = inventoryItemRepository.save(InventoryItem.create(
            "Other essence",
            null,
            Category.ESSENCE,
            UnitOfMeasure.MILLILITER
        ));
        var firstBatch = batchRepository.save(Batch.create(
            requestedItem.getId(), null, "REQUESTED-1", new BigDecimal("15.250"),
            LocalDate.of(2026, 8, 20), LocalDate.of(2026, 9, 1)
        ));
        var secondBatch = batchRepository.save(Batch.create(
            requestedItem.getId(), null, "REQUESTED-2", new BigDecimal("30.500"),
            LocalDate.of(2026, 8, 21), LocalDate.of(2026, 9, 2)
        ));
        batchRepository.save(Batch.create(
            otherItem.getId(), null, "OTHER-1", new BigDecimal("99.000"),
            LocalDate.of(2026, 8, 22), LocalDate.of(2026, 9, 3)
        ));

        var batches = batchRepository.findByInventoryItemId(requestedItem.getId());

        assertThat(batches).extracting(Batch::getId)
            .containsExactlyInAnyOrder(firstBatch.getId(), secondBatch.getId());
        assertThat(batches).allSatisfy(batch ->
            assertThat(batch.getInventoryItemId()).isEqualTo(requestedItem.getId())
        );
        assertThat(batches).anySatisfy(batch -> {
            assertThat(batch.getId()).isEqualTo(firstBatch.getId());
            assertThat(batch.getLotCode()).isEqualTo("REQUESTED-1");
            assertThat(batch.getInitialQuantity()).isEqualByComparingTo("15.250");
            assertThat(batch.getCurrentQuantity()).isEqualByComparingTo("15.250");
            assertThat(batch.getReceivedAt()).isEqualTo(LocalDate.of(2026, 8, 20));
            assertThat(batch.getExpiresAt()).isEqualTo(LocalDate.of(2026, 9, 1));
        });
    }

    @Test
    void shouldReturnEmptyListWhenInventoryItemHasNoBatches() {
        assertThat(batchRepository.findByInventoryItemId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void shouldAllowZeroCurrentQuantityAtDatabaseLevel() {
        var inventoryItem = inventoryItemRepository.save(InventoryItem.create(
            "Bottle 200ml",
            null,
            Category.BOTTLE,
            UnitOfMeasure.UNIT
        ));
        var batch = Batch.create(
            inventoryItem.getId(),
            null,
            "B-2026-01",
            new BigDecimal("100.000"),
            LocalDate.of(2026, 8, 24),
            null
        );

        batchRepository.save(batch);

        var updatedRows = jdbcTemplate.update(
            "UPDATE inventory_batch SET current_quantity = ? WHERE id = ?",
            BigDecimal.ZERO,
            batch.getId()
        );

        assertThat(updatedRows).isOne();

        var persistedBatch = batchRepository.findById(batch.getId());

        assertThat(persistedBatch)
            .isPresent()
            .get()
            .satisfies(found ->
                assertThat(found.getCurrentQuantity())
                    .isEqualByComparingTo(BigDecimal.ZERO)
            );
    }

    @Test
    void shouldRejectNegativeCurrentQuantityAtDatabaseLevel() {
        var inventoryItem = inventoryItemRepository.save(InventoryItem.create(
            "Bottle 200ml",
            null,
            Category.BOTTLE,
            UnitOfMeasure.UNIT
        ));
        var batch = Batch.create(
            inventoryItem.getId(),
            null,
            "B-2026-02",
            new BigDecimal("100.000"),
            LocalDate.of(2026, 8, 24),
            null
        );

        batchRepository.save(batch);

        assertThatThrownBy(() -> jdbcTemplate.update(
            "UPDATE inventory_batch SET current_quantity = ? WHERE id = ?",
            new BigDecimal("-0.001"),
            batch.getId()
        ))
            .isInstanceOf(DataIntegrityViolationException.class);

        var persistedBatch = batchRepository.findById(batch.getId());

        assertThat(persistedBatch)
            .isPresent()
            .get()
            .satisfies(found ->
                assertThat(found.getCurrentQuantity())
                    .isEqualByComparingTo("100.000")
            );
    }
}
