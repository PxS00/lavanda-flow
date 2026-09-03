package com.ceudelavanda.lavandaflow.production.application.lot;

import com.ceudelavanda.lavandaflow.catalog.ProductionItemReference;
import com.ceudelavanda.lavandaflow.catalog.ProductionItemReferenceLookup;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.production.domain.ProductionLotSequenceAllocator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AllocateInternalProductionLotCodeTest {

    @Mock
    private ProductionItemReferenceLookup productionItemReferenceLookup;

    @Mock
    private ProductionLotSequenceAllocator productionLotSequenceAllocator;

    private AllocateInternalProductionLotCode allocateInternalProductionLotCode;

    @BeforeEach
    void setUp() {
        allocateInternalProductionLotCode = new AllocateInternalProductionLotCode(
            productionItemReferenceLookup,
            productionLotSequenceAllocator
        );
    }

    @Test
    void shouldFormatCatalogMetadataAndSequenceExactly() {
        var outputItemId = UUID.randomUUID();
        var productionDate = LocalDate.of(2026, 9, 3);
        when(productionItemReferenceLookup.findByInventoryItemId(outputItemId))
            .thenReturn(Optional.of(reference(outputItemId, "027", "BDS")));
        when(productionLotSequenceAllocator.allocate("BDS", "027", 2026, 9))
            .thenReturn(OptionalInt.of(1));

        var result = allocateInternalProductionLotCode.execute(command(outputItemId, productionDate));

        assertThat(result.value()).isEqualTo("BDS-027-001-09-2026");
        verify(productionLotSequenceAllocator).allocate("BDS", "027", 2026, 9);
    }

    @Test
    void shouldKeepLeadingZeroesForSequenceAndMonth() {
        var outputItemId = UUID.randomUUID();
        when(productionItemReferenceLookup.findByInventoryItemId(outputItemId))
            .thenReturn(Optional.of(reference(outputItemId, "003", "SBN")));
        when(productionLotSequenceAllocator.allocate("SBN", "003", 2026, 1))
            .thenReturn(OptionalInt.of(7));

        var result = allocateInternalProductionLotCode.execute(command(outputItemId, LocalDate.of(2026, 1, 5)));

        assertThat(result.value()).isEqualTo("SBN-003-007-01-2026");
    }

    @Test
    void shouldUseNoEssenceSentinelWhenCatalogEssenceReferenceIsAbsent() {
        var outputItemId = UUID.randomUUID();
        when(productionItemReferenceLookup.findByInventoryItemId(outputItemId))
            .thenReturn(Optional.of(reference(outputItemId, null, "BAS")));
        when(productionLotSequenceAllocator.allocate("BAS", "000", 2026, 10))
            .thenReturn(OptionalInt.of(1));

        var result = allocateInternalProductionLotCode.execute(command(outputItemId, LocalDate.of(2026, 10, 1)));

        assertThat(result.value()).isEqualTo("BAS-000-001-10-2026");
        verify(productionLotSequenceAllocator).allocate("BAS", "000", 2026, 10);
    }

    @Test
    void shouldUseProductionDateForSequenceScopeAndFormatting() {
        var outputItemId = UUID.randomUUID();
        when(productionItemReferenceLookup.findByInventoryItemId(outputItemId))
            .thenReturn(Optional.of(reference(outputItemId, "122", "BDS")));
        when(productionLotSequenceAllocator.allocate("BDS", "122", 2027, 12))
            .thenReturn(OptionalInt.of(999));

        var result = allocateInternalProductionLotCode.execute(command(outputItemId, LocalDate.of(2027, 12, 31)));

        assertThat(result.value()).isEqualTo("BDS-122-999-12-2027");
        verify(productionLotSequenceAllocator).allocate("BDS", "122", 2027, 12);
    }

    @Test
    void shouldRejectMissingCatalogItem() {
        var outputItemId = UUID.randomUUID();
        when(productionItemReferenceLookup.findByInventoryItemId(outputItemId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> allocateInternalProductionLotCode.execute(
            command(outputItemId, LocalDate.of(2026, 9, 3))
        )).isInstanceOf(ProductionLotCodeCatalogItemNotFoundException.class);
    }

    @Test
    void shouldRejectOutputItemWithoutProductionTypeCode() {
        var outputItemId = UUID.randomUUID();
        when(productionItemReferenceLookup.findByInventoryItemId(outputItemId))
            .thenReturn(Optional.of(reference(outputItemId, "027", null)));

        assertThatThrownBy(() -> allocateInternalProductionLotCode.execute(
            command(outputItemId, LocalDate.of(2026, 9, 3))
        )).isInstanceOf(MissingProductionTypeCodeException.class);
    }

    @Test
    void shouldRejectSequenceExhaustion() {
        var outputItemId = UUID.randomUUID();
        when(productionItemReferenceLookup.findByInventoryItemId(outputItemId))
            .thenReturn(Optional.of(reference(outputItemId, "027", "BDS")));
        when(productionLotSequenceAllocator.allocate("BDS", "027", 2026, 9))
            .thenReturn(OptionalInt.empty());

        assertThatThrownBy(() -> allocateInternalProductionLotCode.execute(
            command(outputItemId, LocalDate.of(2026, 9, 3))
        )).isInstanceOf(ProductionLotSequenceExhaustedException.class);
    }

    private static AllocateInternalProductionLotCodeCommand command(UUID outputItemId, LocalDate productionDate) {
        return new AllocateInternalProductionLotCodeCommand(outputItemId, productionDate);
    }

    private static ProductionItemReference reference(UUID id, String essenceReference, String productionTypeCode) {
        return new ProductionItemReference(id, UnitOfMeasure.LITER, true, essenceReference, productionTypeCode);
    }
}
