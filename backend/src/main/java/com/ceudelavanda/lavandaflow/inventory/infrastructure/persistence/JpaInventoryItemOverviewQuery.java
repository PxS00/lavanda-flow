package com.ceudelavanda.lavandaflow.inventory.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.inventory.application.overview.InventoryItemOverviewMetrics;
import com.ceudelavanda.lavandaflow.inventory.application.overview.InventoryItemOverviewQuery;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.UUID;

/** PostgreSQL projection for one inventory item's operational overview metrics. */
@Repository
@RequiredArgsConstructor
class JpaInventoryItemOverviewQuery implements InventoryItemOverviewQuery {

    private static final String SQL = """
        SELECT
            COALESCE(SUM(batch.current_quantity), CAST(0 AS NUMERIC(19, 6))),
            COALESCE(SUM(
                CASE
                    WHEN batch.current_quantity > 0
                         AND (batch.expires_at IS NULL OR batch.expires_at > :asOfDate)
                    THEN batch.current_quantity
                    ELSE CAST(0 AS NUMERIC(19, 6))
                END
            ), CAST(0 AS NUMERIC(19, 6))),
            (
                SELECT minimum.minimum_quantity
                FROM inventory_minimum_stock_level minimum
                WHERE minimum.inventory_item_id = :inventoryItemId
            ),
            COUNT(*) FILTER (WHERE batch.current_quantity > 0),
            MIN(batch.expires_at) FILTER (
                WHERE batch.current_quantity > 0
                  AND batch.expires_at > :asOfDate
            ),
            COUNT(*) FILTER (
                WHERE batch.current_quantity > 0
                  AND batch.expires_at IS NOT NULL
                  AND batch.expires_at <= :asOfDate
            ),
            COUNT(*) FILTER (
                WHERE batch.current_quantity > 0
                  AND batch.expires_at > :asOfDate
                  AND batch.expires_at <= :expirationCutoff
            )
        FROM inventory_batch batch
        WHERE batch.inventory_item_id = :inventoryItemId
        """;

    private final EntityManager entityManager;

    @Override
    public InventoryItemOverviewMetrics findMetrics(
        UUID inventoryItemId,
        LocalDate asOfDate,
        LocalDate expirationCutoff
    ) {
        var row = (Object[]) entityManager.createNativeQuery(SQL)
            .setParameter("inventoryItemId", inventoryItemId)
            .setParameter("asOfDate", asOfDate)
            .setParameter("expirationCutoff", expirationCutoff)
            .getSingleResult();

        return new InventoryItemOverviewMetrics(
            (BigDecimal) row[0],
            (BigDecimal) row[1],
            (BigDecimal) row[2],
            ((Number) row[3]).longValue(),
            toLocalDate(row[4]),
            ((Number) row[5]).longValue(),
            ((Number) row[6]).longValue()
        );
    }

    private static LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof Date date) {
            return date.toLocalDate();
        }
        throw new IllegalStateException("Unsupported database date value: " + value.getClass().getName());
    }
}
