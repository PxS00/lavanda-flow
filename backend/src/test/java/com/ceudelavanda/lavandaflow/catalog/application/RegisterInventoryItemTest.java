package com.ceudelavanda.lavandaflow.catalog.application;

import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItem;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterInventoryItemTest {

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @Test
    void shouldRegisterActiveInventoryItemUsingDomainNormalization() {
        when(inventoryItemRepository.save(any(InventoryItem.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        var useCase = new RegisterInventoryItem(inventoryItemRepository);

        var result = useCase.execute(new RegisterInventoryItemCommand(
            "  Lavender Essence  ",
            "  Floral raw material  ",
            Category.ESSENCE,
            UnitOfMeasure.MILLILITER
        ));

        assertThat(result.id()).isNotNull();
        assertThat(result.name()).isEqualTo("Lavender Essence");
        assertThat(result.description()).isEqualTo("Floral raw material");
        assertThat(result.category()).isEqualTo(Category.ESSENCE);
        assertThat(result.unitOfMeasure()).isEqualTo(UnitOfMeasure.MILLILITER);
        assertThat(result.active()).isTrue();
        verify(inventoryItemRepository).save(any(InventoryItem.class));
    }

    @Test
    void shouldRegisterProductionReferenceMetadata() {
        when(inventoryItemRepository.save(any(InventoryItem.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        var useCase = new RegisterInventoryItem(inventoryItemRepository);

        var result = useCase.execute(new RegisterInventoryItemCommand(
            "Lavender Essence", null, Category.ESSENCE, UnitOfMeasure.MILLILITER, "014", "ESS"
        ));

        assertThat(result.essenceReference()).isEqualTo("014");
        assertThat(result.productionTypeCode()).isEqualTo("ESS");
    }

    @Test
    void shouldRejectBlankNameBeforePersistence() {
        var useCase = new RegisterInventoryItem(inventoryItemRepository);

        assertThatThrownBy(() -> useCase.execute(new RegisterInventoryItemCommand(
            "   ", null, Category.ESSENCE, UnitOfMeasure.MILLILITER
        )))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("name must not be blank");

        verify(inventoryItemRepository, never()).save(any());
    }
}
