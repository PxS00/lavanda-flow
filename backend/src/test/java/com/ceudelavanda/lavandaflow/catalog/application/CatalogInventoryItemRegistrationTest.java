package com.ceudelavanda.lavandaflow.catalog.application;

import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogInventoryItemRegistrationTest {

    @Mock
    private RegisterInventoryItem registerInventoryItem;

    @Test
    void shouldDelegateFixedEssenceRegistrationToExistingCatalogPolicy() {
        var id = UUID.randomUUID();
        when(registerInventoryItem.execute(org.mockito.ArgumentMatchers.any()))
            .thenReturn(new InventoryItemResult(
                id, "Scandall (M)", null, Category.ESSENCE, UnitOfMeasure.MILLILITER, true
            ));
        var registration = new CatalogInventoryItemRegistration(registerInventoryItem);

        var result = registration.registerEssence("Scandall (M)");

        var command = ArgumentCaptor.forClass(RegisterInventoryItemCommand.class);
        verify(registerInventoryItem).execute(command.capture());
        assertThat(command.getValue().name()).isEqualTo("Scandall (M)");
        assertThat(command.getValue().description()).isNull();
        assertThat(command.getValue().category()).isEqualTo(Category.ESSENCE);
        assertThat(command.getValue().unitOfMeasure()).isEqualTo(UnitOfMeasure.MILLILITER);
        assertThat(command.getValue().essenceReference()).isNull();
        assertThat(command.getValue().productionTypeCode()).isNull();
        assertThat(result.id()).isEqualTo(id);
        assertThat(result.active()).isTrue();
    }
}
