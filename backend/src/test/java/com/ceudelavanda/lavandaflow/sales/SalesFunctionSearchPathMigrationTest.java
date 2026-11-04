package com.ceudelavanda.lavandaflow.sales;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SalesFunctionSearchPathMigrationTest {
    private static final List<String> FUNCTIONS = List.of(
        "prevent_confirmed_sale_mutation", "prevent_referenced_movement_mutation",
        "prevent_sale_allocation_mutation", "prevent_sale_line_snapshot_mutation",
        "prevent_sale_stock_correction_mutation");
    private static final List<String> TABLES = List.of(
        "inventory_batch", "sales_order", "sales_order_line", "sale_allocation",
        "stock_movement", "sale_stock_correction");
    private static final String FUNCTION_METADATA = """
        SELECT p.oid, p.proname, p.prosrc, p.proowner, p.proacl::text,
               p.prosecdef, p.prolang, p.provolatile, p.prorettype, p.proargtypes::text,
               p.proisstrict, p.proleakproof, p.proparallel, p.prokind
        FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
        WHERE n.nspname = ? ORDER BY p.oid
        """;
    private static final String SETTINGS = """
        SELECT p.proname, p.proconfig::text FROM pg_proc p
        JOIN pg_namespace n ON n.oid = p.pronamespace
        WHERE n.nspname = ? ORDER BY p.proname, p.oid
        """;
    private static final String TRIGGERS = """
        SELECT t.oid, t.tgfoid, t.tgrelid, t.tgenabled, pg_get_triggerdef(t.oid) AS definition
        FROM pg_trigger t JOIN pg_class c ON c.oid = t.tgrelid
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = ? ORDER BY t.oid
        """;

    @Autowired private DataSource source;
    @Autowired private Flyway flyway;

    @Test
    void cleanPublicSchemaPinsAllFiveSalesFunctions() {
        assertThat(flyway.info().pending()).isEmpty();
        flyway.validate();
        assertPinned(new JdbcTemplate(source), "public");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void migratesAlternateSchemaAndPreservesImmutableHistory(boolean incremental) throws Exception {
        var schema = "issue286_" + UUID.randomUUID().toString().replace("-", "");
        var jdbc = new JdbcTemplate(source);
        var publicBefore = jdbc.queryForList(SETTINGS, "public");
        try {
            if (incremental) {
                Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
                    .target("21").load().migrate();
                seedHistory(jdbc, schema);
                var functionsBefore = jdbc.queryForList(FUNCTION_METADATA, schema);
                var settingsBefore = jdbc.queryForList(SETTINGS, schema);
                var triggersBefore = jdbc.queryForList(TRIGGERS, schema);
                var indexesSql = "SELECT indexdef FROM pg_indexes WHERE schemaname = ? ORDER BY indexname";
                var indexesBefore = jdbc.queryForList(indexesSql, schema);
                var rowsBefore = rows(jdbc, schema);

                var upgrade = configuredFlyway(schema);
                assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
                assertThat(upgrade.info().current().getVersion().getVersion()).isEqualTo("22");
                assertThat(jdbc.queryForList(FUNCTION_METADATA, schema)).isEqualTo(functionsBefore);
                assertThat(jdbc.queryForList(TRIGGERS, schema)).isEqualTo(triggersBefore);
                assertThat(jdbc.queryForList(indexesSql, schema)).isEqualTo(indexesBefore);
                assertThat(rows(jdbc, schema)).isEqualTo(rowsBefore);
                var settingsAfter = jdbc.queryForList(SETTINGS, schema);
                assertThat(settingsAfter.stream().filter(row -> !settingsBefore.contains(row))
                    .map(row -> (String) row.get("proname")).toList()).containsExactlyElementsOf(FUNCTIONS);
                assertThat(settingsBefore.stream().filter(row -> FUNCTIONS.contains(row.get("proname"))))
                    .allSatisfy(row -> assertThat(row.get("proconfig")).isNull());
            } else {
                configuredFlyway(schema).migrate();
                seedHistory(jdbc, schema);
            }
            var migrated = configuredFlyway(schema);
            migrated.validate();
            assertThat(migrated.info().pending()).isEmpty();
            assertThat(migrated.migrate().migrationsExecuted).isZero();
            assertPinned(jdbc, schema);
            assertThat(jdbc.queryForList(SETTINGS, "public")).isEqualTo(publicBefore);

            try (var connection = source.getConnection()) {
                var isolated = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
                var originalPath = isolated.queryForObject("SELECT current_setting('search_path')", String.class);
                try {
                    isolated.execute("SET search_path = pg_catalog");
                    assertHistoryImmutable(isolated, schema);
                    assertMutableBranches(isolated, schema);
                } finally {
                    isolated.queryForObject("SELECT set_config('search_path', ?, false)", String.class, originalPath);
                }
            }
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    private Flyway configuredFlyway(String schema) {
        return Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).load();
    }

    private void assertPinned(JdbcTemplate jdbc, String schema) {
        assertThat(jdbc.queryForList("""
            SELECT p.proname FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
            CROSS JOIN LATERAL pg_options_to_table(p.proconfig) config
            WHERE n.nspname = ? AND p.pronargs = 0 AND p.prorettype = 'trigger'::regtype
              AND config.option_name = 'search_path' AND config.option_value = '""'
              AND p.proname IN ('prevent_confirmed_sale_mutation', 'prevent_referenced_movement_mutation',
                'prevent_sale_allocation_mutation', 'prevent_sale_line_snapshot_mutation',
                'prevent_sale_stock_correction_mutation')
            ORDER BY p.proname
            """, String.class, schema)).containsExactlyElementsOf(FUNCTIONS);
    }

    private List<List<Map<String, Object>>> rows(JdbcTemplate jdbc, String schema) {
        return TABLES.stream().map(table -> jdbc.queryForList(
            "SELECT * FROM " + schema + "." + table + " ORDER BY id")).toList();
    }

    private void seedHistory(JdbcTemplate jdbc, String schema) {
        var item = UUID.randomUUID();
        var batch = UUID.randomUUID();
        var sale = UUID.randomUUID();
        var line = UUID.randomUUID();
        var consumed = UUID.randomUUID();
        var returned = UUID.randomUUID();
        jdbc.update("INSERT INTO " + schema + ".inventory_item (id,name,category,default_unit) "
            + "VALUES (?, 'Product', 'FINISHED_PRODUCT', 'UNIT')", item);
        jdbc.update("INSERT INTO " + schema + ".inventory_batch "
            + "(id,inventory_item_id,initial_quantity,current_quantity,received_at) VALUES (?, ?, 5, 4, current_date)", batch, item);
        jdbc.update("INSERT INTO " + schema + ".sales_order (id,status,total,created_at,updated_at,confirmed_at) "
            + "VALUES (?, 'CONFIRMED', 2, current_timestamp, current_timestamp, current_timestamp)", sale);
        jdbc.update("INSERT INTO " + schema + ".sales_order_line "
            + "(id,order_id,item_id,position,quantity,unit_price,amount,item_name,unit_of_measure) "
            + "VALUES (?, ?, ?, 0, 2, 1, 2, 'Product', 'UNIT')", line, sale, item);
        for (var movement : List.of(consumed, returned)) {
            jdbc.update("INSERT INTO " + schema + ".stock_movement "
                + "(id,batch_id,movement_type,quantity,occurred_at,reference_type,reference_id,reference_line_id) "
                + "VALUES (?, ?, ?, ?, current_timestamp, 'SALE', ?, ?)", movement, batch,
                movement.equals(consumed) ? "CONSUMPTION" : "ADJUSTMENT_IN",
                movement.equals(consumed) ? 2 : 1, sale, line);
        }
        jdbc.update("INSERT INTO " + schema + ".sale_allocation "
            + "(id,line_id,batch_id,movement_id,quantity,position) VALUES (?, ?, ?, ?, 2, 0)",
            UUID.randomUUID(), line, batch, consumed);
        jdbc.update("INSERT INTO " + schema + ".sale_stock_correction "
            + "(sale_id,id,line_id,batch_id,quantity,reason,inspected,accepted,actor,occurred_at,movement_id) "
            + "VALUES (?, ?, ?, ?, 1, 'Inspected', true, true, 'operator', current_timestamp, ?)",
            sale, UUID.randomUUID(), line, batch, returned);
    }

    private void assertHistoryImmutable(JdbcTemplate jdbc, String schema) {
        var before = rows(jdbc, schema);
        var assignments = Map.of("sales_order", "total = total", "sales_order_line", "item_name = item_name",
            "sale_allocation", "quantity = quantity", "stock_movement", "quantity = quantity",
            "sale_stock_correction", "reason = reason");
        var messages = Map.of("sales_order", "confirmed sale history is immutable",
            "sales_order_line", "confirmed sale line history is immutable",
            "sale_allocation", "sale allocation history is immutable",
            "stock_movement", "referenced stock movement history is immutable",
            "sale_stock_correction", "sale stock correction history is immutable");
        for (var table : assignments.keySet()) {
            for (var statement : List.of("UPDATE " + schema + "." + table + " SET " + assignments.get(table),
                                        "DELETE FROM " + schema + "." + table)) {
                assertThatThrownBy(() -> jdbc.update(statement)).isInstanceOf(DataAccessException.class)
                    .hasMessageContaining(messages.get(table)).rootCause()
                    .isInstanceOfSatisfying(java.sql.SQLException.class,
                        error -> assertThat(error.getSQLState()).isEqualTo("P0001"));
            }
        }
        assertThat(rows(jdbc, schema)).isEqualTo(before);
    }

    private void assertMutableBranches(JdbcTemplate jdbc, String schema) {
        var draft = UUID.randomUUID();
        var line = UUID.randomUUID();
        var item = jdbc.queryForObject("SELECT id FROM " + schema + ".inventory_item", UUID.class);
        var batch = jdbc.queryForObject("SELECT id FROM " + schema + ".inventory_batch", UUID.class);
        jdbc.update("INSERT INTO " + schema + ".sales_order (id,total,created_at,updated_at) "
            + "VALUES (?, 1, current_timestamp, current_timestamp)", draft);
        jdbc.update("INSERT INTO " + schema + ".sales_order_line "
            + "(id,order_id,item_id,position,quantity,unit_price,amount) VALUES (?, ?, ?, 0, 1, 1, 1)", line, draft, item);
        assertThat(jdbc.update("UPDATE " + schema + ".sales_order SET total = 2 WHERE id = ?", draft)).isEqualTo(1);
        assertThat(jdbc.update("UPDATE " + schema + ".sales_order_line SET quantity = 2, amount = 2 WHERE id = ?", line)).isEqualTo(1);
        assertThat(jdbc.update("DELETE FROM " + schema + ".sales_order_line WHERE id = ?", line)).isEqualTo(1);
        assertThat(jdbc.update("DELETE FROM " + schema + ".sales_order WHERE id = ?", draft)).isEqualTo(1);
        jdbc.update("INSERT INTO " + schema + ".sales_order (id,total,created_at,updated_at) "
            + "VALUES (?, 0, current_timestamp, current_timestamp)", draft);
        assertThat(jdbc.update("UPDATE " + schema + ".sales_order SET status = 'CONFIRMED', "
            + "confirmed_at = current_timestamp WHERE id = ?", draft)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM " + schema + ".sales_order WHERE id = ?", draft))
            .isInstanceOf(DataAccessException.class).hasMessageContaining("confirmed sale history is immutable");
        var legacy = UUID.randomUUID();
        jdbc.update("INSERT INTO " + schema + ".stock_movement (id,batch_id,movement_type,quantity,occurred_at) "
            + "VALUES (?, ?, 'ENTRY', 1, current_timestamp)", legacy, batch);
        assertThat(jdbc.update("UPDATE " + schema + ".stock_movement SET quantity = 2 WHERE id = ?", legacy)).isEqualTo(1);
        assertThat(jdbc.update("DELETE FROM " + schema + ".stock_movement WHERE id = ?", legacy)).isEqualTo(1);
    }
}
