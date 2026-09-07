package com.ceudelavanda.lavandaflow.inventory.application.initialsnapshot;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import com.ceudelavanda.lavandaflow.inventory.domain.StockMovement;
import com.ceudelavanda.lavandaflow.inventory.domain.StockMovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import({
    TestcontainersConfiguration.class,
    ImportInitialInventorySnapshotRollbackIntegrationTest.FixedClockConfiguration.class
})
class ImportInitialInventorySnapshotRollbackIntegrationTest {

    @Autowired private ImportInitialInventorySnapshot importer;
    @Autowired private JdbcTemplate jdbcTemplate;

    @TempDir
    Path directory;

    @BeforeEach
    void clearInventory() {
        jdbcTemplate.update("DELETE FROM stock_movement");
        jdbcTemplate.update("DELETE FROM inventory_batch");
        jdbcTemplate.update("DELETE FROM inventory_minimum_stock_level");
        jdbcTemplate.update("DELETE FROM inventory_item");
    }

    @Test
    void shouldRollbackAllCatalogAndInventoryWritesWhenLaterReceiptFails() throws Exception {
        var file = directory.resolve("rollback.csv");
        Files.writeString(file, """
            Nome do Perfume,Genero,Ml Disponiveis,Expired
            First,F,1,02/24
            Second,M,2,03/24
            """, StandardCharsets.UTF_8);

        assertThatThrownBy(() -> importer.execute(
            InitialInventoryImportMode.APPLY, file, LocalDate.of(2024, 1, 10)
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("simulated second movement failure");

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM inventory_item", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM inventory_batch", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM stock_movement", Integer.class)).isZero();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock initialInventoryImportRollbackClock() {
            return Clock.fixed(Instant.parse("2024-01-15T12:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        @Primary
        StockMovementRepository failingAfterSecondPersistedMovement(
            @Qualifier("jpaStockMovementRepository") StockMovementRepository delegate
        ) {
            var saves = new AtomicInteger();
            return new StockMovementRepository() {
                @Override
                public StockMovement save(StockMovement movement) {
                    var saved = delegate.save(movement);
                    if (saves.incrementAndGet() == 2) {
                        throw new IllegalStateException("simulated second movement failure");
                    }
                    return saved;
                }

                @Override
                public List<StockMovement> findByBatchIdOrderByOccurredAtAsc(UUID batchId) {
                    return delegate.findByBatchIdOrderByOccurredAtAsc(batchId);
                }
            };
        }
    }
}
