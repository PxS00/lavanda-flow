package com.ceudelavanda.lavandaflow.customers;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CustomerMigrationTest {
    @Autowired private DataSource dataSource;

    @Test void upgradesV16PreservingLegacyDataAndAllowsOldAndNewWritersAfterRetry() {
        var schema = "issue260_" + UUID.randomUUID().toString().replace("-", "");
        var jdbc = new JdbcTemplate(dataSource);
        try {
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("16").load().migrate();
            var supplierId = UUID.randomUUID();
            jdbc.update("insert into " + schema + ".supplier (id, name, contact) values (?, 'Legacy supplier', 'legacy@example.test')", supplierId);
            var before = jdbc.queryForMap("select * from " + schema + ".supplier where id = ?", supplierId);
            var upgrade = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load();
            assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(upgrade.migrate().migrationsExecuted).isZero();
            upgrade.validate();
            assertThat(jdbc.queryForMap("select * from " + schema + ".supplier where id = ?", supplierId)).isEqualTo(before);
            jdbc.update("insert into " + schema + ".supplier (id, name) values (?, 'Old version writer')", UUID.randomUUID());
            var customerId = UUID.randomUUID();
            jdbc.update("insert into " + schema + ".customer (id, name) values (?, 'Name-only contact')", customerId);
            assertThat(jdbc.queryForMap("select * from " + schema + ".customer where id = ?", customerId))
                .containsEntry("active", true).containsEntry("phone", null).containsEntry("email", null)
                .containsKeys("created_at", "updated_at");
            assertThat(jdbc.queryForObject("select indexdef from pg_indexes where schemaname = ? and indexname = 'idx_customer_active_name_id'", String.class, schema))
                .contains("(active, name, id)");
            assertThat(jdbc.queryForObject("select count(*) from " + schema + ".supplier", Long.class)).isEqualTo(2);
        } finally {
            jdbc.execute("drop schema if exists " + schema + " cascade");
        }
    }
}
