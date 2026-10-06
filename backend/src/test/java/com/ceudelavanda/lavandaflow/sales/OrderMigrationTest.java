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
class OrderMigrationTest {
    @Autowired DataSource source;
    @Test void upgradesV17PreservesOldDataSupportsBothWritersAndRetriesWithoutContraction() {
        var schema = "issue261_" + UUID.randomUUID().toString().replace("-", "");
        var jdbc = new JdbcTemplate(source);
        try {
            Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).target("17").load().migrate();
            var customer = UUID.randomUUID(); var item = UUID.randomUUID();
            jdbc.update("insert into " + schema + ".customer (id, name) values (?, 'Existing')", customer);
            jdbc.update("insert into " + schema + ".inventory_item (id, name, category, default_unit) values (?, 'Product', 'FINISHED_PRODUCT', 'UNIT')", item);
            var before = jdbc.queryForMap("select * from " + schema + ".customer where id = ?", customer);
            var flyway = Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);
            assertThat(flyway.migrate().migrationsExecuted).isZero(); flyway.validate();
            assertThat(jdbc.queryForMap("select * from " + schema + ".customer where id = ?", customer)).isEqualTo(before);
            jdbc.update("insert into " + schema + ".customer (id, name) values (?, 'Old writer')", UUID.randomUUID());
            var order = UUID.randomUUID();
            jdbc.update("insert into " + schema + ".sales_order (id, customer_id, total, created_at, updated_at) values (?, NULL, 0, current_timestamp, current_timestamp)", order);
            jdbc.update("insert into " + schema + ".sales_order_line (id, order_id, item_id, position, quantity, unit_price, amount) values (?, ?, ?, 0, 0.000001, 0, 0)", UUID.randomUUID(), order, item);
            assertThat(jdbc.queryForMap("select * from " + schema + ".sales_order where id = ?", order)).containsEntry("customer_id", null).containsEntry("status", "DRAFT");
            assertThat(jdbc.queryForObject("select count(*) from " + schema + ".customer", Long.class)).isEqualTo(2);
        } finally { jdbc.execute("drop schema if exists " + schema + " cascade"); }
    }
}
