package com.ceudelavanda.lavandaflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class FlywayMigrationTest {

    @Autowired
    private Flyway flyway;

    @Test
    void appliesFoundationBaselineMigration() {
        var currentMigration = flyway.info().current();

        assertNotNull(currentMigration);
        assertNotNull(currentMigration.getVersion());
        assertEquals("1", currentMigration.getVersion().getVersion());
    }
}
