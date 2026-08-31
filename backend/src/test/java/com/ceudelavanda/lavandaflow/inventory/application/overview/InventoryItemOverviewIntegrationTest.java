package com.ceudelavanda.lavandaflow.inventory.application.overview;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.catalog.application.RegisterInventoryItem;
import com.ceudelavanda.lavandaflow.catalog.application.RegisterInventoryItemCommand;
import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import com.ceudelavanda.lavandaflow.inventory.application.alerts.GetExpirationAlerts;
import com.ceudelavanda.lavandaflow.inventory.application.alerts.GetExpirationAlertsQuery;
import com.ceudelavanda.lavandaflow.inventory.application.stock.GetCurrentStock;
import com.ceudelavanda.lavandaflow.inventory.application.stock.GetCurrentStockQuery;
import com.ceudelavanda.lavandaflow.inventory.domain.Batch;
import com.ceudelavanda.lavandaflow.inventory.domain.BatchRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.MinimumStockLevel;
import com.ceudelavanda.lavandaflow.inventory.domain.MinimumStockLevelRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import({TestcontainersConfiguration.class, InventoryItemOverviewIntegrationTest.FixedClockConfiguration.class})
class InventoryItemOverviewIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 31);

    @Autowired private RegisterInventoryItem registerInventoryItem;
    @Autowired private GetInventoryItemOverview getOverview;
    @Autowired private GetCurrentStock getCurrentStock;
    @Autowired private GetExpirationAlerts getExpirationAlerts;
    @Autowired private BatchRepository batchRepository;
    @Autowired private MinimumStockLevelRepository minimumStockLevelRepository;

    @Test
    void shouldAggregateOverviewWithExistingStockAndExpirationSemantics() {
        var item = registerInventoryItem.execute(new RegisterInventoryItemCommand(
            "Overview Essence " + UUID.randomUUID(), null, Category.ESSENCE, UnitOfMeasure.MILLILITER
        ));
        saveBatch(item.id(), "5.000000", TODAY.minusDays(40), TODAY);
        saveBatch(item.id(), "3.500000", TODAY.minusDays(20), TODAY.plusDays(5));
        saveBatch(item.id(), "7.250000", TODAY.minusDays(10), TODAY.plusDays(60));
        saveBatch(item.id(), "2.125000", TODAY.minusDays(5), null);
        saveBatch(item.id(), "0.000000", TODAY.minusDays(2), TODAY.plusDays(2));
        minimumStockLevelRepository.save(new MinimumStockLevel(item.id(), new BigDecimal("15.000000")));

        var overview = getOverview.execute(new GetInventoryItemOverviewQuery(item.id(), 30));
        var current = getCurrentStock.execute(new GetCurrentStockQuery(item.id(), true));
        var expiration = getExpirationAlerts.execute(new GetExpirationAlertsQuery(30)).alerts().stream()
            .filter(alert -> alert.inventoryItemId().equals(item.id()))
            .toList();

        assertThat(overview.name()).isEqualTo(item.name());
        assertThat(overview.category()).isEqualTo("ESSENCE");
        assertThat(overview.unitOfMeasure()).isEqualTo(UnitOfMeasure.MILLILITER);
        assertThat(overview.active()).isTrue();
        assertThat(overview.totalCurrentQuantity()).isEqualByComparingTo("17.875000");
        assertThat(overview.totalCurrentQuantity()).isEqualByComparingTo(current.totalCurrentQuantity());
        assertThat(overview.availableQuantity()).isEqualByComparingTo("12.875000");
        assertThat(overview.minimumQuantity()).isEqualByComparingTo("15.000000");
        assertThat(overview.lowStock()).isTrue();
        assertThat(overview.outOfStock()).isFalse();
        assertThat(overview.nonZeroBatchCount()).isEqualTo(4);
        assertThat(overview.nearestExpiration()).isEqualTo(TODAY.plusDays(5));
        assertThat(overview.expiredBatchCount()).isEqualTo(1);
        assertThat(overview.expiringSoonBatchCount()).isEqualTo(1);
        assertThat(expiration).hasSize(2);
        assertThat(expiration).anySatisfy(alert -> assertThat(alert.expiresAt()).isEqualTo(TODAY));
        assertThat(expiration).anySatisfy(alert -> assertThat(alert.expiresAt()).isEqualTo(TODAY.plusDays(5)));
    }

    @Test
    void shouldReturnZeroedOverviewForExistingItemWithoutInventoryConfiguration() {
        var item = registerInventoryItem.execute(new RegisterInventoryItemCommand(
            "Empty Overview " + UUID.randomUUID(), null, Category.OTHER, UnitOfMeasure.UNIT
        ));

        var overview = getOverview.execute(new GetInventoryItemOverviewQuery(item.id(), 30));

        assertThat(overview.totalCurrentQuantity()).isEqualByComparingTo("0.000000");
        assertThat(overview.availableQuantity()).isEqualByComparingTo("0.000000");
        assertThat(overview.minimumQuantity()).isNull();
        assertThat(overview.lowStock()).isFalse();
        assertThat(overview.outOfStock()).isTrue();
        assertThat(overview.nonZeroBatchCount()).isZero();
        assertThat(overview.nearestExpiration()).isNull();
        assertThat(overview.expiredBatchCount()).isZero();
        assertThat(overview.expiringSoonBatchCount()).isZero();
    }

    private void saveBatch(UUID itemId, String quantity, LocalDate receivedAt, LocalDate expiresAt) {
        var currentQuantity = new BigDecimal(quantity);
        var initialQuantity = currentQuantity.signum() == 0 ? BigDecimal.ONE.setScale(6) : currentQuantity;
        batchRepository.save(new Batch(
            UUID.randomUUID(), itemId, null, null,
            initialQuantity, currentQuantity, receivedAt, expiresAt
        ));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock inventoryOverviewClock() {
            return Clock.fixed(Instant.parse("2026-08-31T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        }
    }
}
