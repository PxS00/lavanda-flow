package com.ceudelavanda.lavandaflow;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class FunctionSearchPathMigrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void upgradesV20InConfiguredSchemaWithoutChangingBodiesPrivilegesTriggersOrIndexes() throws Exception {
        var schema = "issue282_" + UUID.randomUUID().toString().replace("-", "");
        var jdbc = new JdbcTemplate(dataSource);
        try {
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .target("20").load().migrate();
            var functionsSql = """
                SELECT p.proname, p.prosrc, p.proowner, p.proacl::text, p.prosecdef
                FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
                WHERE n.nspname = ? ORDER BY p.proname
                """;
            var triggersSql = """
                SELECT pg_get_triggerdef(t.oid) AS definition
                FROM pg_trigger t JOIN pg_class c ON c.oid = t.tgrelid
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = ? AND NOT t.tgisinternal ORDER BY t.tgname
                """;
            var indexesSql = "SELECT indexdef FROM pg_indexes WHERE schemaname = ? ORDER BY indexname";
            var functionsBefore = jdbc.queryForList(functionsSql, schema);
            var triggersBefore = jdbc.queryForList(triggersSql, schema);
            var indexesBefore = jdbc.queryForList(indexesSql, schema);
            var publicSettingsBefore = jdbc.queryForList("""
                SELECT p.proname, p.proconfig::text FROM pg_proc p
                JOIN pg_namespace n ON n.oid = p.pronamespace
                WHERE n.nspname = 'public' ORDER BY p.proname, p.oid
                """);

            var flyway = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(flyway.info().pending()).isEmpty();
            assertThat(jdbc.queryForList(functionsSql, schema)).isEqualTo(functionsBefore);
            assertThat(jdbc.queryForList(triggersSql, schema)).isEqualTo(triggersBefore);
            assertThat(jdbc.queryForList(indexesSql, schema)).isEqualTo(indexesBefore);
            assertThat(jdbc.queryForList("""
                SELECT p.proname, p.proconfig::text FROM pg_proc p
                JOIN pg_namespace n ON n.oid = p.pronamespace
                WHERE n.nspname = 'public' ORDER BY p.proname, p.oid
                """)).isEqualTo(publicSettingsBefore);
            assertThat(jdbc.queryForList("""
                SELECT p.proname FROM pg_proc p
                JOIN pg_namespace n ON n.oid = p.pronamespace
                CROSS JOIN LATERAL pg_options_to_table(p.proconfig) config
                WHERE n.nspname = ? AND config.option_name = 'search_path' AND config.option_value = '""'
                ORDER BY p.proname
                """, String.class, schema)).containsExactly(
                    "prevent_canonical_essence_reclassification",
                    "prevent_inventory_item_essence_reference_deletion",
                    "prevent_inventory_item_production_reference_mutation",
                    "prevent_production_history_mutation"
                );

            // Use a caller search path that cannot resolve application objects.
            try (var connection = dataSource.getConnection()) {
                var isolated = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
                var originalSearchPath = isolated.queryForObject("SELECT current_setting('search_path')", String.class);
                try {
                    isolated.execute("SET search_path = pg_catalog");
                    var id = UUID.randomUUID();
                    isolated.update("INSERT INTO " + schema + ".inventory_item "
                        + "(id, name, category, default_unit, essence_reference, production_type_code) "
                        + "VALUES (?, 'Migration essence', 'ESSENCE', 'MILLILITER', '282', 'ESS')", id);
                    assertThat(isolated.update("UPDATE " + schema + ".inventory_item SET name = 'Renamed' WHERE id = ?", id))
                        .isEqualTo(1);
                    assertThatThrownBy(() -> isolated.update("UPDATE " + schema
                        + ".inventory_item SET essence_reference = '283' WHERE id = ?", id))
                        .isInstanceOf(DataAccessException.class).hasMessageContaining("assigned essence_reference cannot be changed");
                    assertThatThrownBy(() -> isolated.update("UPDATE " + schema
                        + ".inventory_item SET category = 'FINISHED_PRODUCT' WHERE id = ?", id))
                        .isInstanceOf(DataAccessException.class).hasMessageContaining("canonical essence");
                    assertThatThrownBy(() -> isolated.update("DELETE FROM " + schema + ".inventory_item WHERE id = ?", id))
                        .isInstanceOf(DataAccessException.class).hasMessageContaining("cannot be deleted");
                } finally {
                    isolated.queryForObject("SELECT set_config('search_path', ?, false)", String.class, originalSearchPath);
                }
            }
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }
}
