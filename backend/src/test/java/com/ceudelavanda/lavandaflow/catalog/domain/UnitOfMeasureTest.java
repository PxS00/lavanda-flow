package com.ceudelavanda.lavandaflow.catalog.domain;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UnitOfMeasureTest {

    @Test
    void shouldContainApprovedUnits() {
        var expected = Set.of(
            UnitOfMeasure.MILLILITER,
            UnitOfMeasure.LITER,
            UnitOfMeasure.GRAM,
            UnitOfMeasure.KILOGRAM,
            UnitOfMeasure.UNIT
        );

        assertEquals(expected, Set.of(UnitOfMeasure.values()));
    }

    @Test
    void shouldRejectUnsupportedUnit() {
        assertThrows(
            IllegalArgumentException.class,
            () -> UnitOfMeasure.valueOf("OUNCE")
        );
    }
}
