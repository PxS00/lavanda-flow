package com.ceudelavanda.lavandaflow.inventory.application;

import com.ceudelavanda.lavandaflow.catalog.InventoryItemLookup;
import com.ceudelavanda.lavandaflow.catalog.InventoryItemSnapshot;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.inventory.application.minimumstock.ConfigureMinimumStockLevel;
import com.ceudelavanda.lavandaflow.inventory.application.minimumstock.DeleteMinimumStockLevel;
import com.ceudelavanda.lavandaflow.inventory.application.minimumstock.GetMinimumStockLevel;
import com.ceudelavanda.lavandaflow.inventory.domain.MinimumStockLevel;
import com.ceudelavanda.lavandaflow.inventory.domain.MinimumStockLevelRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InventoryItemNotFoundException;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.MinimumStockLevelNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MinimumStockLevelConfigurationTest {

    @Mock private InventoryItemLookup inventoryItemLookup;
    @Mock private MinimumStockLevelRepository minimumStockLevelRepository;

    private ConfigureMinimumStockLevel configureMinimumStockLevel;
    private GetMinimumStockLevel getMinimumStockLevel;
    private DeleteMinimumStockLevel deleteMinimumStockLevel;

    @BeforeEach
    void setUp() {
        configureMinimumStockLevel = new ConfigureMinimumStockLevel(inventoryItemLookup, minimumStockLevelRepository);
        getMinimumStockLevel = new GetMinimumStockLevel(inventoryItemLookup, minimumStockLevelRepository);
        deleteMinimumStockLevel = new DeleteMinimumStockLevel(inventoryItemLookup, minimumStockLevelRepository);
    }

    @Test
    void shouldCreateAndUpdateMinimumStockLevelForInactiveItem() {
        var itemId = UUID.randomUUID();
        when(inventoryItemLookup.findById(itemId)).thenReturn(Optional.of(inactiveItem(itemId)));
        when(minimumStockLevelRepository.findByInventoryItemId(itemId)).thenReturn(Optional.empty());
        when(minimumStockLevelRepository.save(org.mockito.ArgumentMatchers.any(MinimumStockLevel.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var created = configureMinimumStockLevel.execute(itemId, new BigDecimal("10"));

        assertThat(created.created()).isTrue();
        assertThat(created.level().minimumQuantity()).isEqualByComparingTo("10.000000");

        var existing = new MinimumStockLevel(itemId, new BigDecimal("10"));
        when(minimumStockLevelRepository.findByInventoryItemId(itemId)).thenReturn(Optional.of(existing));
        var updated = configureMinimumStockLevel.execute(itemId, new BigDecimal("20.5"));

        assertThat(updated.created()).isFalse();
        assertThat(updated.level().minimumQuantity()).isEqualByComparingTo("20.500000");
    }

    @Test
    void shouldRetrieveAndIdempotentlyDeleteExistingItemConfiguration() {
        var itemId = UUID.randomUUID();
        when(inventoryItemLookup.findById(itemId)).thenReturn(Optional.of(inactiveItem(itemId)));
        when(minimumStockLevelRepository.findByInventoryItemId(itemId))
            .thenReturn(Optional.of(new MinimumStockLevel(itemId, new BigDecimal("10"))));

        var result = getMinimumStockLevel.execute(itemId);
        deleteMinimumStockLevel.execute(itemId);

        assertThat(result.inventoryItemId()).isEqualTo(itemId);
        verify(minimumStockLevelRepository).deleteByInventoryItemId(itemId);
    }

    @Test
    void shouldReportMissingConfigurationAndUnknownItemWithoutRepositoryOperations() {
        var configuredItemId = UUID.randomUUID();
        when(inventoryItemLookup.findById(configuredItemId)).thenReturn(Optional.of(inactiveItem(configuredItemId)));
        when(minimumStockLevelRepository.findByInventoryItemId(configuredItemId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> getMinimumStockLevel.execute(configuredItemId))
            .isInstanceOf(MinimumStockLevelNotFoundException.class);

        var unknownItemId = UUID.randomUUID();
        when(inventoryItemLookup.findById(unknownItemId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> configureMinimumStockLevel.execute(unknownItemId, BigDecimal.ONE))
            .isInstanceOf(InventoryItemNotFoundException.class);
        assertThatThrownBy(() -> deleteMinimumStockLevel.execute(unknownItemId))
            .isInstanceOf(InventoryItemNotFoundException.class);
        verify(minimumStockLevelRepository, never()).deleteByInventoryItemId(unknownItemId);
    }

    private static InventoryItemSnapshot inactiveItem(UUID itemId) {
        return new InventoryItemSnapshot(itemId, "Inactive item", UnitOfMeasure.UNIT, false);
    }
}
