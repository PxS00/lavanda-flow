package com.ceudelavanda.lavandaflow.production.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.production.domain.PackagedProductionLotSequenceAllocator;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.OptionalInt;

/** PostgreSQL implementation of the transactional packaged-output monthly sequence. */
@Component
@RequiredArgsConstructor
class JdbcPackagedProductionLotSequenceAllocator implements PackagedProductionLotSequenceAllocator {

    private static final String ALLOCATE_SEQUENCE = """
        INSERT INTO packaged_production_lot_sequence (
            production_year,
            production_month,
            last_sequence
        ) VALUES (?, ?, 1)
        ON CONFLICT (production_year, production_month)
        DO UPDATE SET last_sequence = packaged_production_lot_sequence.last_sequence + 1
        WHERE packaged_production_lot_sequence.last_sequence < 999
        RETURNING last_sequence
        """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public OptionalInt allocate(int productionYear, int productionMonth) {
        return jdbcTemplate.query(
            ALLOCATE_SEQUENCE,
            statement -> {
                statement.setInt(1, productionYear);
                statement.setInt(2, productionMonth);
            },
            (resultSet, rowNum) -> resultSet.getInt("last_sequence")
        ).stream().mapToInt(Integer::intValue).findFirst();
    }
}
