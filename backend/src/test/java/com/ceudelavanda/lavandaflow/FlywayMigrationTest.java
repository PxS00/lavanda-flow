package com.ceudelavanda.lavandaflow;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class FlywayMigrationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private DataSource dataSource;

    @Test
    void upgradesV14WithoutRewritingExistingCatalogMetadata() {
        var schema = "issue229_" + UUID.randomUUID().toString().replace("-", "");
        var jdbc = new JdbcTemplate(dataSource);
        try {
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .target("14").load().migrate();
            var id = UUID.randomUUID();
            jdbc.update("insert into " + schema + ".inventory_item "
                + "(id, name, category, default_unit, active, essence_reference, production_type_code) "
                + "values (?, 'Legacy essence', 'ESSENCE', 'MILLILITER', true, '229', 'ESS')", id);
            jdbc.update("insert into " + schema + ".inventory_item "
                + "(id, name, category, default_unit, active) values (?, 'Legacy bottle', 'BOTTLE', 'UNIT', true)",
                UUID.randomUUID());
            var upgraded = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load();
            upgraded.migrate();
            assertThat(upgraded.info().pending()).isEmpty();
            assertThat(jdbc.queryForObject("select essence_reference from " + schema + ".inventory_item where id = ?",
                String.class, id)).isEqualTo("229");
            assertThat(jdbc.queryForObject("select count(*) from " + schema + ".inventory_item where product_gender is null",
                Integer.class)).isEqualTo(2);
            jdbc.update("insert into " + schema + ".inventory_item "
                + "(id, name, category, default_unit, active, essence_reference, product_gender) "
                + "values (?, 'Perfume', 'FINISHED_PRODUCT', 'UNIT', true, '229', 'M/C')", UUID.randomUUID());
        } finally {
            jdbc.execute("drop schema if exists " + schema + " cascade");
        }
    }

    @Test
    void appliesAllDatabaseMigrations() {
        var pendingMigrations = flyway.info().pending();

        assertThat(pendingMigrations).isEmpty();
    }
}
