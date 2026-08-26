package com.ceudelavanda.lavandaflow.inventory.application;

import com.ceudelavanda.lavandaflow.inventory.application.query.GetExpirationAlertsQuery;
import com.ceudelavanda.lavandaflow.inventory.application.result.ExpirationAlertStatus;
import com.ceudelavanda.lavandaflow.inventory.domain.Batch;
import com.ceudelavanda.lavandaflow.inventory.domain.BatchRepository;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InvalidExpirationAlertWindowException;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetExpirationAlertsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 26);
    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2026-08-26T12:00:00Z"),
        ZoneId.of("America/Sao_Paulo")
    );

    @Mock
    private BatchRepository batchRepository;

    private GetExpirationAlerts getExpirationAlerts;

    @BeforeEach
    void setUp() {
        getExpirationAlerts = new GetExpirationAlerts(batchRepository, CLOCK);
    }

    @Test
    void shouldClassifyExpiredAndExpiringSoonBatchesAtDateBoundaries() {
        var itemId = uuid(100);
        var expired = batch(itemId, uuid(1), "EXPIRED", "10", TODAY.minusDays(6));
        var expiresToday = batch(itemId, uuid(2), "TODAY", "20", TODAY);
        var expiresTomorrow = batch(itemId, uuid(3), "TOMORROW", "30", TODAY.plusDays(1));
        var expiresAtCutoff = batch(itemId, uuid(4), "CUTOFF", "40", TODAY.plusDays(30));
        when(batchRepository.findWithPositiveBalanceExpiringOnOrBefore(TODAY.plusDays(30)))
            .thenReturn(List.of(expiresTomorrow, expiresAtCutoff, expired, expiresToday));

        var result = getExpirationAlerts.execute(new GetExpirationAlertsQuery(30));

        assertThat(result.asOfDate()).isEqualTo(TODAY);
        assertThat(result.windowDays()).isEqualTo(30);
        assertThat(result.alerts()).extracting(alert -> alert.batchId()).containsExactly(
            expired.getId(), expiresToday.getId(), expiresTomorrow.getId(), expiresAtCutoff.getId()
        );
        assertThat(result.alerts()).extracting(alert -> alert.daysUntilExpiration())
            .containsExactly(-6L, 0L, 1L, 30L);
        assertThat(result.alerts()).extracting(alert -> alert.status()).containsExactly(
            ExpirationAlertStatus.EXPIRED,
            ExpirationAlertStatus.EXPIRED,
            ExpirationAlertStatus.EXPIRING_SOON,
            ExpirationAlertStatus.EXPIRING_SOON
        );
        verify(batchRepository).findWithPositiveBalanceExpiringOnOrBefore(TODAY.plusDays(30));
    }

    @Test
    void shouldUseOnlyExpiredCandidatesWhenWindowIsZero() {
        var itemId = uuid(100);
        var expired = batch(itemId, uuid(1), "EXPIRED", "10", TODAY.minusDays(1));
        var expiresToday = batch(itemId, uuid(2), "TODAY", "20", TODAY);
        when(batchRepository.findWithPositiveBalanceExpiringOnOrBefore(TODAY))
            .thenReturn(List.of(expiresToday, expired));

        var result = getExpirationAlerts.execute(new GetExpirationAlertsQuery(0));

        assertThat(result.windowDays()).isZero();
        assertThat(result.alerts()).extracting(alert -> alert.status())
            .containsOnly(ExpirationAlertStatus.EXPIRED);
        assertThat(result.alerts()).extracting(alert -> alert.daysUntilExpiration())
            .containsExactly(-1L, 0L);
        verify(batchRepository).findWithPositiveBalanceExpiringOnOrBefore(TODAY);
    }

    @Test
    void shouldOrderSameExpirationByItemAndBatchIdentifiers() {
        var expiration = TODAY.plusDays(5);
        var lowerItem = uuid(10);
        var higherItem = uuid(20);
        var first = batch(lowerItem, uuid(2), "FIRST", "1", expiration);
        var second = batch(lowerItem, uuid(3), "SECOND", "1", expiration);
        var third = batch(higherItem, uuid(1), "THIRD", "1", expiration);
        when(batchRepository.findWithPositiveBalanceExpiringOnOrBefore(TODAY.plusDays(10)))
            .thenReturn(List.of(third, second, first));

        var result = getExpirationAlerts.execute(new GetExpirationAlertsQuery(10));

        assertThat(result.alerts()).extracting(alert -> alert.batchId())
            .containsExactly(first.getId(), second.getId(), third.getId());
    }

    @Test
    void shouldPreserveNullableLotCode() {
        var itemId = uuid(100);
        var batch = batch(itemId, uuid(1), null, "7.25", TODAY.plusDays(2));
        when(batchRepository.findWithPositiveBalanceExpiringOnOrBefore(TODAY.plusDays(5)))
            .thenReturn(List.of(batch));

        var result = getExpirationAlerts.execute(new GetExpirationAlertsQuery(5));

        assertThat(result.alerts()).singleElement().satisfies(alert -> {
            assertThat(alert.inventoryItemId()).isEqualTo(itemId);
            assertThat(alert.lotCode()).isNull();
            assertThat(alert.currentQuantity()).isEqualByComparingTo("7.25");
            assertThat(alert.expiresAt()).isEqualTo(TODAY.plusDays(2));
            assertThat(alert.daysUntilExpiration()).isEqualTo(2);
            assertThat(alert.status()).isEqualTo(ExpirationAlertStatus.EXPIRING_SOON);
        });
    }

    @Test
    void shouldRejectNegativeAlertWindow() {
        assertThatThrownBy(() -> new GetExpirationAlertsQuery(-1))
            .isInstanceOf(InvalidExpirationAlertWindowException.class)
            .hasMessage("Expiration alert window must be zero or positive: -1");
    }

    private static Batch batch(
        UUID itemId,
        UUID batchId,
        String lotCode,
        String currentQuantity,
        LocalDate expiresAt
    ) {
        return new Batch(
            batchId,
            itemId,
            null,
            lotCode,
            BigDecimal.TEN,
            new BigDecimal(currentQuantity),
            TODAY.minusMonths(2),
            expiresAt
        );
    }

    private static UUID uuid(long value) {
        return new UUID(0, value);
    }
}
