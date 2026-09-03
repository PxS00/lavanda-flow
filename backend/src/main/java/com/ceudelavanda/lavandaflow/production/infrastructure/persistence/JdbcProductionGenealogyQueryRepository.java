package com.ceudelavanda.lavandaflow.production.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.production.application.genealogy.ProductionGenealogyEdgeRecord;
import com.ceudelavanda.lavandaflow.production.application.genealogy.ProductionGenealogyQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class JdbcProductionGenealogyQueryRepository implements ProductionGenealogyQueryRepository {

    private static final String UPSTREAM_SQL = """
        WITH RECURSIVE genealogy AS (
            SELECT pe.id AS execution_id,
                   pe.formula_id,
                   pc.source_batch_id,
                   pe.output_batch_id,
                   pc.quantity AS consumed_quantity,
                   pe.production_date,
                   pe.completed_at,
                   ARRAY[pe.output_batch_id, pc.source_batch_id]::uuid[] AS path
            FROM production_execution pe
            JOIN production_consumption pc ON pc.execution_id = pe.id
            WHERE pe.output_batch_id = :batchId

            UNION ALL

            SELECT pe.id,
                   pe.formula_id,
                   pc.source_batch_id,
                   pe.output_batch_id,
                   pc.quantity,
                   pe.production_date,
                   pe.completed_at,
                   genealogy.path || pc.source_batch_id
            FROM genealogy
            JOIN production_execution pe ON pe.output_batch_id = genealogy.source_batch_id
            JOIN production_consumption pc ON pc.execution_id = pe.id
            WHERE NOT pc.source_batch_id = ANY(genealogy.path)
        )
        SELECT DISTINCT execution_id,
                        formula_id,
                        source_batch_id,
                        output_batch_id,
                        consumed_quantity,
                        production_date,
                        completed_at
        FROM genealogy
        ORDER BY completed_at, execution_id, source_batch_id, output_batch_id
        """;

    private static final String DOWNSTREAM_SQL = """
        WITH RECURSIVE genealogy AS (
            SELECT pe.id AS execution_id,
                   pe.formula_id,
                   pc.source_batch_id,
                   pe.output_batch_id,
                   pc.quantity AS consumed_quantity,
                   pe.production_date,
                   pe.completed_at,
                   ARRAY[pc.source_batch_id, pe.output_batch_id]::uuid[] AS path
            FROM production_consumption pc
            JOIN production_execution pe ON pe.id = pc.execution_id
            WHERE pc.source_batch_id = :batchId

            UNION ALL

            SELECT pe.id,
                   pe.formula_id,
                   pc.source_batch_id,
                   pe.output_batch_id,
                   pc.quantity,
                   pe.production_date,
                   pe.completed_at,
                   genealogy.path || pe.output_batch_id
            FROM genealogy
            JOIN production_consumption pc ON pc.source_batch_id = genealogy.output_batch_id
            JOIN production_execution pe ON pe.id = pc.execution_id
            WHERE NOT pe.output_batch_id = ANY(genealogy.path)
        )
        SELECT DISTINCT execution_id,
                        formula_id,
                        source_batch_id,
                        output_batch_id,
                        consumed_quantity,
                        production_date,
                        completed_at
        FROM genealogy
        ORDER BY completed_at, execution_id, source_batch_id, output_batch_id
        """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public List<ProductionGenealogyEdgeRecord> findUpstreamEdges(UUID batchId) {
        return queryEdges(UPSTREAM_SQL, batchId);
    }

    @Override
    public List<ProductionGenealogyEdgeRecord> findDownstreamEdges(UUID batchId) {
        return queryEdges(DOWNSTREAM_SQL, batchId);
    }

    @Override
    public Set<UUID> findProducedBatchIds(Collection<UUID> batchIds) {
        if (batchIds == null || batchIds.isEmpty()) {
            return Set.of();
        }
        var parameters = new MapSqlParameterSource("batchIds", batchIds);
        return Set.copyOf(new LinkedHashSet<>(jdbcTemplate.query(
            "SELECT output_batch_id FROM production_execution WHERE output_batch_id IN (:batchIds)",
            parameters,
            (resultSet, rowNum) -> resultSet.getObject("output_batch_id", UUID.class)
        )));
    }

    private List<ProductionGenealogyEdgeRecord> queryEdges(String sql, UUID batchId) {
        return jdbcTemplate.query(
            sql,
            new MapSqlParameterSource("batchId", batchId),
            (resultSet, rowNum) -> toEdge(resultSet)
        );
    }

    private ProductionGenealogyEdgeRecord toEdge(ResultSet resultSet) throws SQLException {
        return new ProductionGenealogyEdgeRecord(
            resultSet.getObject("execution_id", UUID.class),
            resultSet.getObject("formula_id", UUID.class),
            resultSet.getObject("source_batch_id", UUID.class),
            resultSet.getObject("output_batch_id", UUID.class),
            resultSet.getBigDecimal("consumed_quantity"),
            resultSet.getObject("production_date", java.time.LocalDate.class),
            resultSet.getTimestamp("completed_at").toInstant()
        );
    }
}
