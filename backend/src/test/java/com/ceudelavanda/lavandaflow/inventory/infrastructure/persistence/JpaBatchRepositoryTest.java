package com.ceudelavanda.lavandaflow.inventory.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItem;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItemRepository;
import com.ceudelavanda.lavandaflow.catalog.domain.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.inventory.domain.Batch;
import com.ceudelavanda.lavandaflow.inventory.domain.BatchRepository;
import com.ceudelavanda.lavandaflow.suppliers.domain.Supplier;
import com.ceudelavanda.lavandaflow.suppliers.domain.SupplierRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class JpaBatchRepositoryTest {

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private SupplierRepository supplierRepository;

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
}
