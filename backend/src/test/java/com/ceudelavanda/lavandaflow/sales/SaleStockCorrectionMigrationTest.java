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
class SaleStockCorrectionMigrationTest {
    @Autowired DataSource source;

    @Test void v19UpgradePreservesHistoryAndLegacyWritersAllowsPartialReturnsAndStillRejectsDuplicateConsumption() {
        var schema = "issue276_" + UUID.randomUUID().toString().replace("-", "");
        var jdbc = new JdbcTemplate(source);
        try {
            Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).target("19").load().migrate();
            var item = UUID.randomUUID(); var batch = UUID.randomUUID(); var sale = UUID.randomUUID();
            var line = UUID.randomUUID(); var consumed = UUID.randomUUID();
            jdbc.update("insert into " + schema + ".inventory_item (id,name,category,default_unit) values (?, 'Legacy', 'FINISHED_PRODUCT', 'UNIT')", item);
            jdbc.update("insert into " + schema + ".inventory_batch (id,inventory_item_id,initial_quantity,current_quantity,received_at,expires_at) values (?, ?, 5, 3, current_date, current_date + 2)", batch, item);
            jdbc.update("insert into " + schema + ".sales_order (id,status,total,created_at,updated_at,confirmed_at) values (?, 'CONFIRMED', 2, current_timestamp,current_timestamp,current_timestamp)", sale);
            jdbc.update("insert into " + schema + ".sales_order_line (id,order_id,item_id,position,quantity,unit_price,amount,item_name,unit_of_measure) values (?, ?, ?, 0, 2, 1, 2, 'Legacy', 'UNIT')", line, sale, item);
            insertMovement(jdbc, schema, consumed, batch, "CONSUMPTION", sale, line);
            jdbc.update("insert into " + schema + ".sale_allocation (id,line_id,batch_id,movement_id,quantity,position) values (?, ?, ?, ?, 2, 0)", UUID.randomUUID(), line, batch, consumed);
            var oldMovement = jdbc.queryForList("select * from " + schema + ".stock_movement");
            var oldSale = jdbc.queryForList("select * from " + schema + ".sales_order");
            var oldLines = jdbc.queryForList("select * from " + schema + ".sales_order_line");
            var oldAllocations = jdbc.queryForList("select * from " + schema + ".sale_allocation");
            var oldBatches = jdbc.queryForList("select * from " + schema + ".inventory_batch");
            assertThatThrownBy(() -> insertMovement(jdbc, schema, UUID.randomUUID(), batch, "ADJUSTMENT_IN", sale, line))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            var upgrade = Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).load();
            assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(upgrade.migrate().migrationsExecuted).isZero(); upgrade.validate();
            assertThat(jdbc.queryForList("select * from " + schema + ".stock_movement")).isEqualTo(oldMovement);
            assertThat(jdbc.queryForList("select * from " + schema + ".sales_order")).isEqualTo(oldSale);
            assertThat(jdbc.queryForList("select * from " + schema + ".sales_order_line")).isEqualTo(oldLines);
            assertThat(jdbc.queryForList("select * from " + schema + ".sale_allocation")).isEqualTo(oldAllocations);
            assertThat(jdbc.queryForList("select * from " + schema + ".inventory_batch")).isEqualTo(oldBatches);
            // V19 confirmation writer still works, and repeated positive returns no longer collide with it.
            assertThatThrownBy(() -> insertMovement(jdbc, schema, UUID.randomUUID(), batch, "CONSUMPTION", sale, line))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            var first = UUID.randomUUID(); var second = UUID.randomUUID(); var correction = UUID.randomUUID();
            insertMovement(jdbc, schema, first, batch, "ADJUSTMENT_IN", sale, line);
            insertMovement(jdbc, schema, second, batch, "ADJUSTMENT_IN", sale, line);
            insertCorrection(jdbc, schema, sale, correction, line, batch, first, true);
            assertThatThrownBy(() -> insertCorrection(jdbc, schema, sale, correction, line, batch, second, true))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThatThrownBy(() -> insertCorrection(jdbc, schema, sale, UUID.randomUUID(), line, batch, second, false))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            insertCorrection(jdbc, schema, sale, UUID.randomUUID(), line, batch, second, true);
            assertThat(jdbc.queryForObject("select count(*) from " + schema + ".sale_stock_correction", Long.class)).isEqualTo(2);
            // Legacy unreferenced inventory inserts and new sale consumption remain supported.
            jdbc.update("insert into " + schema + ".stock_movement (id,batch_id,movement_type,quantity,occurred_at) values (?, ?, 'ENTRY', 1, current_timestamp)", UUID.randomUUID(), batch);
            insertMovement(jdbc, schema, UUID.randomUUID(), batch, "CONSUMPTION", UUID.randomUUID(), UUID.randomUUID());
            assertThatThrownBy(() -> jdbc.update("update " + schema + ".stock_movement set quantity = 2 where id = ?", consumed))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        } finally { jdbc.execute("drop schema if exists " + schema + " cascade"); }
    }
    private void insertMovement(JdbcTemplate jdbc, String schema, UUID id, UUID batch, String type, UUID sale, UUID line) {
        jdbc.update("insert into " + schema + ".stock_movement (id,batch_id,movement_type,quantity,occurred_at,reference_type,reference_id,reference_line_id) values (?, ?, ?, 1, current_timestamp, 'SALE', ?, ?)", id, batch, type, sale, line);
    }
    private void insertCorrection(JdbcTemplate jdbc, String schema, UUID sale, UUID id, UUID line, UUID batch, UUID movement, boolean accepted) {
        jdbc.update("insert into " + schema + ".sale_stock_correction (sale_id,id,line_id,batch_id,quantity,reason,inspected,accepted,actor,occurred_at,movement_id) values (?, ?, ?, ?, 1, 'Inspected', true, ?, 'operator', current_timestamp, ?)", sale, id, line, batch, accepted, movement);
    }
}
