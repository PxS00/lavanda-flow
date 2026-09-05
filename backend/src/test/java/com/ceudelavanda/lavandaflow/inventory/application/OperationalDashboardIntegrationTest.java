package com.ceudelavanda.lavandaflow.inventory.application;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItem;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItemRepository;
import com.ceudelavanda.lavandaflow.inventory.application.dashboard.GetOperationalDashboard;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import({TestcontainersConfiguration.class, OperationalDashboardIntegrationTest.FixedClockConfiguration.class})
@Transactional
class OperationalDashboardIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2000, 1, 10);

    @Autowired private GetOperationalDashboard getOperationalDashboard;
    @Autowired private InventoryItemRepository inventoryItemRepository;
    @Autowired private BatchRepository batchRepository;
    @Autowired private MinimumStockLevelRepository minimumStockLevelRepository;

    @Test
    void shouldCountTheMixedOperationalScenarioFromPostgresqlState() {
        var noBatches = saveItem("No batches", true);
        var minimumBelowAvailable = saveItem("Minimum below available", true);
        var minimumEqualAvailable = saveItem("Minimum equal available", true);
        var minimumAboveAvailable = saveItem("Minimum above available", true);
        var expiredOnly = saveItem("Expired only", true);
        var inactive = saveItem("Inactive", false);

        saveMinimum(noBatches, "2");
        saveMinimum(minimumBelowAvailable, "4");
        saveMinimum(minimumEqualAvailable, "5");
        saveMinimum(minimumAboveAvailable, "6");
        saveMinimum(expiredOnly, "1");
        saveMinimum(inactive, "10");

        saveBatch(minimumBelowAvailable, "2", "2", TODAY.plusDays(31));
        saveBatch(minimumBelowAvailable, "3", "3", null);
        saveBatch(minimumEqualAvailable, "5", "5", TODAY.plusDays(30));
        saveBatch(minimumAboveAvailable, "5", "5", TODAY.plusDays(45));
        saveBatch(minimumAboveAvailable, "7", "0", TODAY.plusDays(5));
        saveBatch(expiredOnly, "2", "2", TODAY.minusDays(1));
        saveBatch(expiredOnly, "3", "3", TODAY);

        var result = getOperationalDashboard.execute(30);

        assertThat(result.asOfDate()).isEqualTo(TODAY);
        assertThat(result.expirationWindowDays()).isEqualTo(30);
        assertThat(result.activeItemCount()).isEqualTo(5);
        assertThat(result.lowStockItemCount()).isEqualTo(3);
        assertThat(result.outOfStockItemCount()).isEqualTo(2);
        assertThat(result.expiringSoonBatchCount()).isEqualTo(1);
        assertThat(result.expiredBatchCount()).isEqualTo(2);
    }

    private UUID saveItem(String name, boolean active) {
        var item = InventoryItem.create(
            name + " " + UUID.randomUUID(), null, Category.ESSENCE, UnitOfMeasure.MILLILITER
        );
        if (!active) {
            item.deactivate();
        }
        return inventoryItemRepository.save(item).getId();
    }

    private void saveMinimum(UUID inventoryItemId, String quantity) {
        minimumStockLevelRepository.save(new MinimumStockLevel(
            inventoryItemId, new BigDecimal(quantity)
        ));
    }

    private void saveBatch(
        UUID inventoryItemId,
        String initialQuantity,
        String currentQuantity,
        LocalDate expiresAt
    ) {
        batchRepository.save(new Batch(
            UUID.randomUUID(), inventoryItemId, null, null,
            new BigDecimal(initialQuantity), new BigDecimal(currentQuantity),
            TODAY.minusMonths(2), expiresAt
        ));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock operationalDashboardClock() {
            return Clock.fixed(
                Instant.parse("2000-01-10T12:00:00Z"),
                ZoneId.of("America/Sao_Paulo")
            );
        }
    }
}
