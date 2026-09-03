package com.ceudelavanda.lavandaflow.catalog.application;

import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItem;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogProductionItemReferenceLookupTest {

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @Test
    void shouldExposeOnlyProductionMetadataThroughPublicImmutableValues() {
        var item = InventoryItem.create(
            "Lavender Essence", null, Category.ESSENCE, UnitOfMeasure.MILLILITER, "014", "ESS"
        );
        when(inventoryItemRepository.findById(item.getId())).thenReturn(Optional.of(item));

        var reference = new CatalogProductionItemReferenceLookup(inventoryItemRepository)
            .findByInventoryItemId(item.getId());

        assertThat(reference).isPresent().get().satisfies(found -> {
            assertThat(found.inventoryItemId()).isEqualTo(item.getId());
            assertThat(found.unitOfMeasure()).isEqualTo(UnitOfMeasure.MILLILITER);
            assertThat(found.active()).isTrue();
            assertThat(found.essenceReference()).isEqualTo("014");
            assertThat(found.productionTypeCode()).isEqualTo("ESS");
        });
    }
}
