package com.ceudelavanda.lavandaflow.inventory.application;

import com.ceudelavanda.lavandaflow.catalog.InventoryItemLookup;
import com.ceudelavanda.lavandaflow.catalog.InventoryItemSnapshot;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.inventory.domain.MinimumStockLevel;
import com.ceudelavanda.lavandaflow.inventory.domain.MinimumStockLevelRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InventoryItemNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetLowStockAlertsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 26);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-26T12:00:00Z"), ZoneId.of("America/Sao_Paulo"));

    @Mock private MinimumStockLevelRepository minimumStockLevelRepository;
    @Mock private InventoryItemLookup inventoryItemLookup;
    @Mock private AvailableStockQuery availableStockQuery;

    private GetLowStockAlerts getLowStockAlerts;

    @BeforeEach
    void setUp() {
        getLowStockAlerts = new GetLowStockAlerts(minimumStockLevelRepository, inventoryItemLookup, availableStockQuery, CLOCK);
    }

    @Test
    void shouldAlertBelowMinimumAndTreatMissingAggregateAsZero() {
        var zeroId = uuid(1);
        var lowId = uuid(2);
        var exactId = uuid(3);
        var inactiveId = uuid(4);
        var levels = List.of(level(zeroId, "10"), level(lowId, "10"), level(exactId, "10"), level(inactiveId, "10"));
        when(minimumStockLevelRepository.findAll()).thenReturn(levels);
        when(inventoryItemLookup.findByIds(List.of(zeroId, lowId, exactId, inactiveId))).thenReturn(List.of(
            item(zeroId, "Zulu", true), item(lowId, "alpha", true), item(exactId, "Equal", true), item(inactiveId, "Inactive", false)
        ));
        when(availableStockQuery.findAvailableStockByInventoryItemIds(List.of(zeroId, lowId, exactId), TODAY)).thenReturn(List.of(
            new AvailableStockBalance(lowId, new BigDecimal("4.5")),
            new AvailableStockBalance(exactId, new BigDecimal("10"))
        ));

        var result = getLowStockAlerts.execute();

        assertThat(result.asOfDate()).isEqualTo(TODAY);
        assertThat(result.alerts()).extracting(alert -> alert.inventoryItemId()).containsExactly(zeroId, lowId);
        assertThat(result.alerts().get(0).availableQuantity()).isEqualByComparingTo("0.000000");
        assertThat(result.alerts().get(0).deficitQuantity()).isEqualByComparingTo("10");
        assertThat(result.alerts().get(1).deficitQuantity()).isEqualByComparingTo("5.5");
        verify(availableStockQuery).findAvailableStockByInventoryItemIds(List.of(zeroId, lowId, exactId), TODAY);
    }

    @Test
    void shouldOrderNonZeroAlertsByDeficitThenCaseInsensitiveNameThenId() {
        var firstId = uuid(1);
        var secondId = uuid(2);
        var thirdId = uuid(3);
        var levels = List.of(level(firstId, "10"), level(secondId, "10"), level(thirdId, "10"));
        when(minimumStockLevelRepository.findAll()).thenReturn(levels);
        when(inventoryItemLookup.findByIds(List.of(firstId, secondId, thirdId))).thenReturn(List.of(
            item(firstId, "beta", true), item(secondId, "Alpha", true), item(thirdId, "alpha", true)
        ));
        when(availableStockQuery.findAvailableStockByInventoryItemIds(List.of(firstId, secondId, thirdId), TODAY)).thenReturn(List.of(
            new AvailableStockBalance(firstId, new BigDecimal("5")),
            new AvailableStockBalance(secondId, new BigDecimal("5")),
            new AvailableStockBalance(thirdId, new BigDecimal("5"))
        ));

        var result = getLowStockAlerts.execute();

        assertThat(result.alerts()).extracting(alert -> alert.inventoryItemId())
            .containsExactly(secondId, thirdId, firstId);
    }

    @Test
    void shouldFailFastWhenConfiguredItemIsMissingFromCatalogBulkLookup() {
        var itemId = uuid(1);
        when(minimumStockLevelRepository.findAll()).thenReturn(List.of(level(itemId, "10")));
        when(inventoryItemLookup.findByIds(List.of(itemId))).thenReturn(List.of());

        assertThatThrownBy(() -> getLowStockAlerts.execute())
            .isInstanceOf(InventoryItemNotFoundException.class);

        verify(availableStockQuery, never()).findAvailableStockByInventoryItemIds(any(), any());
    }

    @Test
    void shouldExcludeInactiveConfiguredItemsWithoutQueryingAvailableStock() {
        var itemId = uuid(1);
        when(minimumStockLevelRepository.findAll()).thenReturn(List.of(level(itemId, "10")));
        when(inventoryItemLookup.findByIds(List.of(itemId))).thenReturn(List.of(item(itemId, "Inactive", false)));

        var result = getLowStockAlerts.execute();

        assertThat(result.alerts()).isEmpty();
        verify(availableStockQuery, never()).findAvailableStockByInventoryItemIds(any(), any());
    }

    private static MinimumStockLevel level(UUID itemId, String quantity) {
        return new MinimumStockLevel(itemId, new BigDecimal(quantity));
    }

    private static InventoryItemSnapshot item(UUID id, String name, boolean active) {
        return new InventoryItemSnapshot(id, name, UnitOfMeasure.UNIT, active);
    }

    private static UUID uuid(long value) {
        return new UUID(0, value);
    }
}
