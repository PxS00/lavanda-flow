package com.ceudelavanda.lavandaflow.inventory.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Defines the exact quantity representation supported by inventory persistence.
 *
 * <p>Inventory quantities must fit PostgreSQL {@code NUMERIC(19,6)} without
 * rounding. Exact values are normalized to six decimal places so domain state
 * and persisted state use the same representation.</p>
 */
public final class StockQuantityRules {

    public static final int PRECISION = 19;
    public static final int SCALE = 6;
    public static final int INTEGER_DIGITS = PRECISION - SCALE;

    private StockQuantityRules() {
    }

    public static BigDecimal requirePositive(BigDecimal value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must be greater than zero");
        }

        var normalized = normalizeExact(value, field);
        if (normalized.signum() <= 0) {
            throw new IllegalArgumentException(field + " must be greater than zero");
        }
        return normalized;
    }

    public static BigDecimal requireNonNegative(BigDecimal value, String field) {
        var normalized = normalizeExact(value, field);
        if (normalized.signum() < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
        return normalized;
    }

    public static BigDecimal requireNonZero(BigDecimal value, String field) {
        var normalized = normalizeExact(value, field);
        if (normalized.signum() == 0) {
            throw new IllegalArgumentException(field + " must not be zero");
        }
        return normalized;
    }

    private static BigDecimal normalizeExact(BigDecimal value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }

        final BigDecimal normalized;
        try {
            normalized = value.setScale(SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw invalidRepresentation(field, value);
        }

        if (normalized.precision() > PRECISION) {
            throw invalidRepresentation(field, value);
        }

        return normalized;
    }

    private static IllegalArgumentException invalidRepresentation(
        String field,
        BigDecimal value
    ) {
        return new IllegalArgumentException(
            field + " must fit NUMERIC(" + PRECISION + "," + SCALE
                + ") without rounding: " + value
        );
    }
}
