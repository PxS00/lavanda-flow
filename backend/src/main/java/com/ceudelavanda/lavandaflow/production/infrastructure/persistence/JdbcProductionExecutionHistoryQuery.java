package com.ceudelavanda.lavandaflow.production.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.production.application.history.GetProductionExecutionHistoryQuery;
import com.ceudelavanda.lavandaflow.production.application.history.ProductionExecutionHistoryQuery;
import com.ceudelavanda.lavandaflow.production.domain.ProductionLotCodeMode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class JdbcProductionExecutionHistoryQuery implements ProductionExecutionHistoryQuery {

    private static final String SUMMARY_COLUMNS = """
        id, formula_id, output_inventory_item_id, output_batch_id, output_quantity,
        lot_code, lot_code_mode, production_date, completed_at
        """;
    private static final String DETAIL_COLUMNS = SUMMARY_COLUMNS + ", output_received_at, output_expires_at";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public Page find(GetProductionExecutionHistoryQuery query) {
        var parameters = new MapSqlParameterSource()
            .addValue("size", query.size())
            .addValue("offset", (long) query.page() * query.size());
        var conditions = new ArrayList<String>();
        if (query.from() != null) {
            conditions.add("production_date >= :from");
            parameters.addValue("from", query.from());
        }
        if (query.to() != null) {
            conditions.add("production_date <= :to");
            parameters.addValue("to", query.to());
        }
        var where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
        var content = jdbcTemplate.query(
            "SELECT " + SUMMARY_COLUMNS + " FROM production_execution" + where
                + " ORDER BY production_date DESC, completed_at DESC, id DESC"
                + " LIMIT :size OFFSET :offset",
            parameters,
            (resultSet, rowNum) -> toSummary(resultSet)
        );
        var totalElements = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM production_execution" + where,
            parameters,
            Long.class
        );
        long total = totalElements == null ? 0 : totalElements;
        int totalPages = total == 0 ? 0 : (int) ((total + query.size() - 1) / query.size());
        return new Page(content, query.page(), query.size(), total, totalPages);
    }

    @Override
    public Optional<Detail> findById(UUID executionId) {
        var parameters = new MapSqlParameterSource("executionId", executionId);
        var executions = jdbcTemplate.query(
            "SELECT " + DETAIL_COLUMNS + " FROM production_execution WHERE id = :executionId",
            parameters,
            (resultSet, rowNum) -> toDetail(resultSet, List.of())
        );
        if (executions.isEmpty()) {
            return Optional.empty();
        }
        var execution = executions.getFirst();
        var consumptions = jdbcTemplate.query(
            """
                SELECT source_batch_id, source_inventory_item_id, movement_id, quantity
                FROM production_consumption
                WHERE execution_id = :executionId
                ORDER BY position ASC
                """,
            parameters,
            (resultSet, rowNum) -> new Consumption(
                resultSet.getObject("source_batch_id", UUID.class),
                resultSet.getObject("source_inventory_item_id", UUID.class),
                resultSet.getObject("movement_id", UUID.class),
                resultSet.getBigDecimal("quantity")
            )
        );
        return Optional.of(new Detail(
            execution.executionId(), execution.formulaId(), execution.outputInventoryItemId(),
            execution.outputBatchId(), execution.outputQuantity(), execution.lotCode(),
            execution.lotCodeMode(), execution.productionDate(), execution.outputReceivedAt(),
            execution.outputExpiresAt(), execution.completedAt(), consumptions
        ));
    }

    private static Summary toSummary(ResultSet resultSet) throws SQLException {
        return new Summary(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("formula_id", UUID.class),
            resultSet.getObject("output_inventory_item_id", UUID.class),
            resultSet.getObject("output_batch_id", UUID.class),
            resultSet.getBigDecimal("output_quantity"),
            resultSet.getString("lot_code"),
            ProductionLotCodeMode.valueOf(resultSet.getString("lot_code_mode")),
            resultSet.getObject("production_date", java.time.LocalDate.class),
            resultSet.getTimestamp("completed_at").toInstant()
        );
    }

    private static Detail toDetail(ResultSet resultSet, List<Consumption> consumptions)
        throws SQLException {
        var summary = toSummary(resultSet);
        return new Detail(
            summary.executionId(), summary.formulaId(), summary.outputInventoryItemId(),
            summary.outputBatchId(), summary.outputQuantity(), summary.lotCode(),
            summary.lotCodeMode(), summary.productionDate(),
            resultSet.getObject("output_received_at", java.time.LocalDate.class),
            resultSet.getObject("output_expires_at", java.time.LocalDate.class),
            summary.completedAt(), consumptions
        );
    }
}
