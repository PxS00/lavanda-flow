package com.ceudelavanda.lavandaflow.sales;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SaleConfirmationMigrationTest {
    @Autowired DataSource source;

    @Test void v18ExpansionPreservesMovementsGenealogyAndDraftsAndSupportsLegacyWriters() {
        var schema = "issue262_" + UUID.randomUUID().toString().replace("-", "");
        var jdbc = new JdbcTemplate(source);
        try {
            Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).target("18").load().migrate();
            var item = UUID.randomUUID(); var batch = UUID.randomUUID(); var output = UUID.randomUUID();
            var movement = UUID.randomUUID(); var formula = UUID.randomUUID(); var execution = UUID.randomUUID();
            var order = UUID.randomUUID(); var line = UUID.randomUUID();
            jdbc.update("insert into " + schema + ".inventory_item (id, name, category, default_unit) values (?, 'Product', 'FINISHED_PRODUCT', 'UNIT')", item);
            for (var id : new UUID[]{batch, output}) jdbc.update("insert into " + schema + ".inventory_batch (id, inventory_item_id, initial_quantity, current_quantity, received_at) values (?, ?, 5, 5, current_date)", id, item);
            jdbc.update("insert into " + schema + ".stock_movement (id, batch_id, movement_type, quantity, occurred_at) values (?, ?, 'CONSUMPTION', 1, current_timestamp)", movement, batch);
            jdbc.update("insert into " + schema + ".production_formula (id, output_inventory_item_id, output_quantity, output_unit_of_measure, formula_kind) values (?, ?, 1, 'UNIT', 'STANDARD')", formula, item);
            jdbc.update("insert into " + schema + ".production_execution (id, formula_id, output_inventory_item_id, output_batch_id, output_quantity, lot_code, lot_code_mode, production_date, output_received_at, completed_at) values (?, ?, ?, ?, 1, 'Legacy', 'MANUAL', current_date, current_date, current_timestamp)", execution, formula, item, output);
            jdbc.update("insert into " + schema + ".production_consumption (execution_id, position, source_batch_id, source_inventory_item_id, movement_id, quantity) values (?, 0, ?, ?, ?, 1)", execution, batch, item, movement);
            jdbc.update("insert into " + schema + ".sales_order (id, total, created_at, updated_at) values (?, 1, current_timestamp, current_timestamp)", order);
            jdbc.update("insert into " + schema + ".sales_order_line (id, order_id, item_id, position, quantity, unit_price, amount) values (?, ?, ?, 0, 1, 1, 1)", line, order, item);
            var oldMovement = jdbc.queryForMap("select * from " + schema + ".stock_movement where id = ?", movement);
            var genealogy = jdbc.queryForList("select * from " + schema + ".production_consumption");
            var executions = jdbc.queryForList("select * from " + schema + ".production_execution");
            var batches = jdbc.queryForList("select * from " + schema + ".inventory_batch order by id");
            var flyway = Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(flyway.migrate().migrationsExecuted).isZero(); flyway.validate();
            assertThat(jdbc.queryForMap("select * from " + schema + ".stock_movement where id = ?", movement))
                .containsAllEntriesOf(oldMovement).containsEntry("reference_type", null).containsEntry("reference_id", null).containsEntry("reference_line_id", null);
            assertThat(jdbc.queryForList("select * from " + schema + ".production_consumption")).isEqualTo(genealogy);
            assertThat(jdbc.queryForList("select * from " + schema + ".production_execution")).isEqualTo(executions);
            assertThat(jdbc.queryForList("select * from " + schema + ".inventory_batch order by id")).isEqualTo(batches);
            assertThat(jdbc.queryForMap("select * from " + schema + ".sales_order where id = ?", order)).containsEntry("status", "DRAFT").containsEntry("confirmed_at", null);
            assertThatThrownBy(() -> jdbc.update("update " + schema + ".sales_order_line set item_name = 'Incomplete', unit_of_measure = NULL where id = ?", line))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("update " + schema + ".sales_order_line set unit_of_measure = 'UNIT', item_name = NULL where id = ?", line))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            jdbc.update("update " + schema + ".sales_order_line set quantity = 2, amount = 2 where id = ?", line);
            jdbc.update("update " + schema + ".sales_order set total = 2 where id = ?", order);
            jdbc.update("insert into " + schema + ".stock_movement (id, batch_id, movement_type, quantity, occurred_at) values (?, ?, 'ENTRY', 1, current_timestamp)", UUID.randomUUID(), batch);
            assertThat(jdbc.queryForObject("select count(*) from " + schema + ".stock_movement", Long.class)).isEqualTo(2);
        } finally { jdbc.execute("drop schema if exists " + schema + " cascade"); }
    }
}
