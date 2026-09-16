package com.ceudelavanda.lavandaflow.catalog.application;

import com.ceudelavanda.lavandaflow.catalog.ProductGender;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItem;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateInventoryItemTest {

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @Test
    void shouldUpdateMetadataAndPreserveIdentityAndImmutableContext() {
        var id = UUID.randomUUID();
        var item = new InventoryItem(id, "Old name", "Old description", Category.FINISHED_PRODUCT,
            UnitOfMeasure.UNIT, true, "014", "PRF", ProductGender.FEMININE);
        when(inventoryItemRepository.findById(id)).thenReturn(Optional.of(item));
        when(inventoryItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = new UpdateInventoryItem(inventoryItemRepository).execute(
            new UpdateInventoryItemCommand(id, "  New name  ", "  New description  ", false, "014", "PRF")
        );

        assertThat(result.id()).isEqualTo(id);
        assertThat(result.name()).isEqualTo("New name");
        assertThat(result.description()).isEqualTo("New description");
        assertThat(result.active()).isFalse();
        assertThat(result.category()).isEqualTo(Category.FINISHED_PRODUCT);
        assertThat(result.unitOfMeasure()).isEqualTo(UnitOfMeasure.UNIT);
        assertThat(result.gender()).isEqualTo(ProductGender.FEMININE);
        assertThat(result.essenceReference()).isEqualTo("014");
        assertThat(result.productionTypeCode()).isEqualTo("PRF");
        verify(inventoryItemRepository).save(item);
    }

    @Test
    void shouldClearDescriptionAndReactivateAnInactiveItem() {
        var id = UUID.randomUUID();
        var item = new InventoryItem(id, "Essence", "Description", Category.ESSENCE,
            UnitOfMeasure.MILLILITER, false);
        when(inventoryItemRepository.findById(id)).thenReturn(Optional.of(item));
        when(inventoryItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = new UpdateInventoryItem(inventoryItemRepository).execute(
            new UpdateInventoryItemCommand(id, "Essence", "   ", true, null, null)
        );

        assertThat(result.description()).isNull();
        assertThat(result.active()).isTrue();
    }

    @Test
    void shouldAssignStableMetadataAndAcceptUnchangedReplays() {
        var id = UUID.randomUUID();
        var item = new InventoryItem(id, "Essence", null, Category.ESSENCE,
            UnitOfMeasure.MILLILITER, true);
        when(inventoryItemRepository.findById(id)).thenReturn(Optional.of(item));
        when(inventoryItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var useCase = new UpdateInventoryItem(inventoryItemRepository);

        useCase.execute(new UpdateInventoryItemCommand(id, "Essence", null, true, "001", "ESS"));
        var replay = useCase.execute(new UpdateInventoryItemCommand(id, "Essence", null, true, "001", "ESS"));

        assertThat(replay.essenceReference()).isEqualTo("001");
        assertThat(replay.productionTypeCode()).isEqualTo("ESS");
    }

    @Test
    void shouldRejectEssenceReferenceReplacementAndRemovalBeforePersistence() {
        var id = UUID.randomUUID();
        var item = new InventoryItem(id, "Essence", null, Category.ESSENCE,
            UnitOfMeasure.MILLILITER, true, "014", "ESS");
        when(inventoryItemRepository.findById(id)).thenReturn(Optional.of(item));
        var useCase = new UpdateInventoryItem(inventoryItemRepository);

        assertThatThrownBy(() -> useCase.execute(
            new UpdateInventoryItemCommand(id, "Essence", null, true, "015", "ESS")
        )).isInstanceOf(InvalidInventoryItemMaintenanceException.class)
            .satisfies(error -> assertThat(((InvalidInventoryItemMaintenanceException) error).getCode())
                .isEqualTo("INVENTORY_ITEM_STABLE_METADATA_IMMUTABLE"));

        assertThatThrownBy(() -> useCase.execute(
            new UpdateInventoryItemCommand(id, "Essence", null, true, null, "ESS")
        )).isInstanceOf(InvalidInventoryItemMaintenanceException.class);

        verify(inventoryItemRepository, never()).save(any());
    }

    @Test
    void shouldRejectProductionTypeCodeReplacementAndRemovalBeforePersistence() {
        var id = UUID.randomUUID();
        var item = new InventoryItem(id, "Essence", null, Category.ESSENCE,
            UnitOfMeasure.MILLILITER, true, "014", "ESS");
        when(inventoryItemRepository.findById(id)).thenReturn(Optional.of(item));
        var useCase = new UpdateInventoryItem(inventoryItemRepository);

        assertThatThrownBy(() -> useCase.execute(
            new UpdateInventoryItemCommand(id, "Essence", null, true, "014", "BDS")
        )).isInstanceOf(InvalidInventoryItemMaintenanceException.class)
            .satisfies(error -> assertThat(((InvalidInventoryItemMaintenanceException) error).getCode())
                .isEqualTo("INVENTORY_ITEM_STABLE_METADATA_IMMUTABLE"));

        assertThatThrownBy(() -> useCase.execute(
            new UpdateInventoryItemCommand(id, "Essence", null, true, "014", null)
        )).isInstanceOf(InvalidInventoryItemMaintenanceException.class);

        verify(inventoryItemRepository, never()).save(any());
    }

    @Test
    void shouldRejectInvalidAndIneligibleEssenceReferenceAssignments() {
        var invalidId = UUID.randomUUID();
        var ineligibleId = UUID.randomUUID();
        when(inventoryItemRepository.findById(invalidId)).thenReturn(Optional.of(new InventoryItem(
            invalidId, "Essence", null, Category.ESSENCE, UnitOfMeasure.MILLILITER, true
        )));
        when(inventoryItemRepository.findById(ineligibleId)).thenReturn(Optional.of(new InventoryItem(
            ineligibleId, "Bottle", null, Category.BOTTLE, UnitOfMeasure.UNIT, true
        )));
        var useCase = new UpdateInventoryItem(inventoryItemRepository);

        assertThatThrownBy(() -> useCase.execute(
            new UpdateInventoryItemCommand(invalidId, "Essence", null, true, "000", null)
        )).isInstanceOf(InvalidInventoryItemMaintenanceException.class)
            .satisfies(error -> assertThat(((InvalidInventoryItemMaintenanceException) error).getCode())
                .isEqualTo("INVALID_INVENTORY_ITEM_METADATA"));
        assertThatThrownBy(() -> useCase.execute(
            new UpdateInventoryItemCommand(ineligibleId, "Bottle", null, true, "001", null)
        )).isInstanceOf(InvalidInventoryItemMaintenanceException.class);

        verify(inventoryItemRepository, never()).save(any());
    }

    @Test
    void shouldReturnStructuredNotFoundError() {
        var id = UUID.randomUUID();
        when(inventoryItemRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new UpdateInventoryItem(inventoryItemRepository).execute(
            new UpdateInventoryItemCommand(id, "Essence", null, true, null, null)
        )).isInstanceOf(InventoryItemNotFoundException.class);
    }
}
