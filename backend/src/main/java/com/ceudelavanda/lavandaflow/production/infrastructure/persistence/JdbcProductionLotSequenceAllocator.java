package com.ceudelavanda.lavandaflow.production.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.production.domain.ProductionLotSequenceAllocator;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.OptionalInt;

/** PostgreSQL implementation of the transactional monthly lot-sequence allocation. */
@Component
@RequiredArgsConstructor
class JdbcProductionLotSequenceAllocator implements ProductionLotSequenceAllocator {

    private static final String ALLOCATE_SEQUENCE = """
        INSERT INTO production_lot_sequence (
            production_type_code,
            essence_reference,
            production_year,
            production_month,
            last_sequence
        ) VALUES (?, ?, ?, ?, 1)
        ON CONFLICT (production_type_code, essence_reference, production_year, production_month)
        DO UPDATE SET last_sequence = production_lot_sequence.last_sequence + 1
        WHERE production_lot_sequence.last_sequence < 999
        RETURNING last_sequence
        """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public OptionalInt allocate(
        String productionTypeCode,
        String essenceReference,
        int productionYear,
        int productionMonth
    ) {
        return jdbcTemplate.query(
            ALLOCATE_SEQUENCE,
            statement -> {
                statement.setString(1, productionTypeCode);
                statement.setString(2, essenceReference);
                statement.setInt(3, productionYear);
                statement.setInt(4, productionMonth);
            },
            (resultSet, rowNum) -> resultSet.getInt("last_sequence")
        ).stream().mapToInt(Integer::intValue).findFirst();
    }
}
