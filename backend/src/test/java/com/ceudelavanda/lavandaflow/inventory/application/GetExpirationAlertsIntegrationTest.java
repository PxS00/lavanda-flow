package com.ceudelavanda.lavandaflow.inventory.application;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItem;
import com.ceudelavanda.lavandaflow.catalog.domain.InventoryItemRepository;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.inventory.application.alerts.ExpirationAlertStatus;
import com.ceudelavanda.lavandaflow.inventory.application.alerts.GetExpirationAlerts;
import com.ceudelavanda.lavandaflow.inventory.application.alerts.GetExpirationAlertsQuery;
import com.ceudelavanda.lavandaflow.inventory.domain.Batch;
import com.ceudelavanda.lavandaflow.inventory.domain.BatchRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class GetExpirationAlertsIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2000, 1, 10);
    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2000-01-10T12:00:00Z"),
        ZoneId.of("America/Sao_Paulo")
    );

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldRetrievePersistedExpirationCandidatesWithDatabaseFiltering() {
        var item = inventoryItemRepository.save(InventoryItem.create(
            "Expiration alerts integration essence",
            null,
            Category.ESSENCE,
            UnitOfMeasure.MILLILITER
        ));

        var expired = saveBatch(item.getId(), uuid(1), "EXPIRED", "10", TODAY.minusDays(6));
        var expiresToday = saveBatch(item.getId(), uuid(2), "TODAY", "20", TODAY);
        var expiringSoon = saveBatch(item.getId(), uuid(3), "SOON", "30", TODAY.plusDays(1));
        var cutoff = saveBatch(item.getId(), uuid(4), "CUTOFF", "40", TODAY.plusDays(30));
        saveBatch(item.getId(), uuid(5), "OUTSIDE", "50", TODAY.plusDays(31));
        saveBatch(item.getId(), uuid(6), "NO-EXPIRY", "60", null);
        var zeroBalance = saveBatch(item.getId(), uuid(7), "ZERO", "70", TODAY.plusDays(5));

        entityManager.flush();
        jdbcTemplate.update(
            "UPDATE inventory_batch SET current_quantity = ? WHERE id = ?",
            BigDecimal.ZERO,
            zeroBalance.getId()
        );
        entityManager.clear();

        var useCase = new GetExpirationAlerts(batchRepository, CLOCK);
        var result = useCase.execute(new GetExpirationAlertsQuery(30));

        assertThat(result.asOfDate()).isEqualTo(TODAY);
        assertThat(result.alerts()).extracting(alert -> alert.batchId()).containsExactly(
            expired.getId(), expiresToday.getId(), expiringSoon.getId(), cutoff.getId()
        );
        assertThat(result.alerts()).extracting(alert -> alert.status()).containsExactly(
            ExpirationAlertStatus.EXPIRED,
            ExpirationAlertStatus.EXPIRED,
            ExpirationAlertStatus.EXPIRING_SOON,
            ExpirationAlertStatus.EXPIRING_SOON
        );
        assertThat(result.alerts()).extracting(alert -> alert.daysUntilExpiration())
            .containsExactly(-6L, 0L, 1L, 30L);

        var zeroWindow = useCase.execute(new GetExpirationAlertsQuery(0));
        assertThat(zeroWindow.alerts()).extracting(alert -> alert.batchId())
            .containsExactly(expired.getId(), expiresToday.getId());
    }

    private Batch saveBatch(
        UUID itemId,
        UUID batchId,
        String lotCode,
        String quantity,
        LocalDate expiresAt
    ) {
        return batchRepository.save(new Batch(
            batchId,
            itemId,
            null,
            lotCode,
            new BigDecimal(quantity),
            new BigDecimal(quantity),
            TODAY.minusMonths(2),
            expiresAt
        ));
    }

    private static UUID uuid(long value) {
        return new UUID(0, value);
    }
}
