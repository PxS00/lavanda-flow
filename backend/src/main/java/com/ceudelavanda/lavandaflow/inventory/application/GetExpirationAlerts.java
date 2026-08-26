package com.ceudelavanda.lavandaflow.inventory.application;

import com.ceudelavanda.lavandaflow.inventory.application.query.GetExpirationAlertsQuery;
import com.ceudelavanda.lavandaflow.inventory.application.result.ExpirationAlertEntryResult;
import com.ceudelavanda.lavandaflow.inventory.application.result.ExpirationAlertStatus;
import com.ceudelavanda.lavandaflow.inventory.application.result.ExpirationAlertsResult;
import com.ceudelavanda.lavandaflow.inventory.domain.Batch;
import com.ceudelavanda.lavandaflow.inventory.domain.BatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;

/**
 * Retrieves positive-balance inventory batches that are expired or approaching expiration.
 *
 * <p>The query is evaluated against the application {@link Clock}. A batch is considered expired
 * when its expiration date is on or before the evaluation date. A future batch is considered
 * expiring soon when its expiration date is on or before the inclusive alert cutoff.</p>
 *
 * <p>Batches without an expiration date and zero-balance batches are excluded by the persistence
 * query. This read model is informational and does not define stock-consumption eligibility.</p>
 */
@Service
@RequiredArgsConstructor
public class GetExpirationAlerts {

    private static final Comparator<Batch> ALERT_ORDER = Comparator
        .comparing(Batch::getExpiresAt)
        .thenComparing(Batch::getInventoryItemId)
        .thenComparing(Batch::getId);

    private final BatchRepository batchRepository;
    private final Clock clock;

    /**
     * Executes the expiration-alert query for the requested future alert window.
     *
     * @param query resolved non-negative alert window in days
     * @return immutable expiration-alert result evaluated for the application date
     */
    @Transactional(readOnly = true)
    public ExpirationAlertsResult execute(GetExpirationAlertsQuery query) {
        var today = LocalDate.now(clock);
        var cutoff = today.plusDays(query.windowDays());
        var alerts = batchRepository.findWithPositiveBalanceExpiringOnOrBefore(cutoff).stream()
            .sorted(ALERT_ORDER)
            .map(batch -> toAlert(batch, today))
            .toList();

        return new ExpirationAlertsResult(today, query.windowDays(), alerts);
    }

    private ExpirationAlertEntryResult toAlert(Batch batch, LocalDate today) {
        var expiresAt = batch.getExpiresAt();
        var daysUntilExpiration = ChronoUnit.DAYS.between(today, expiresAt);
        var status = expiresAt.isAfter(today)
            ? ExpirationAlertStatus.EXPIRING_SOON
            : ExpirationAlertStatus.EXPIRED;

        return new ExpirationAlertEntryResult(
            batch.getInventoryItemId(),
            batch.getId(),
            batch.getLotCode(),
            batch.getCurrentQuantity(),
            expiresAt,
            daysUntilExpiration,
            status
        );
    }
}
