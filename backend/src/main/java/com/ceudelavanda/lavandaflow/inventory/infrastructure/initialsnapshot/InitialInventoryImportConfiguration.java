package com.ceudelavanda.lavandaflow.inventory.infrastructure.initialsnapshot;

import com.ceudelavanda.lavandaflow.inventory.application.initialsnapshot.ImportInitialInventorySnapshot;
import com.ceudelavanda.lavandaflow.inventory.application.initialsnapshot.InitialInventoryImportException;
import com.ceudelavanda.lavandaflow.inventory.application.initialsnapshot.InitialInventoryImportReport;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InitialInventoryImportProperties.class)
class InitialInventoryImportConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "lavanda.inventory.initial-import", name = "enabled", havingValue = "true")
    InitialInventoryImportRunner initialInventoryImportRunner(
        InitialInventoryImportProperties properties,
        ImportInitialInventorySnapshot importer
    ) {
        return new InitialInventoryImportRunner(properties, importer);
    }

    @RequiredArgsConstructor
    static final class InitialInventoryImportRunner implements ApplicationRunner {

        private static final Logger LOGGER = LoggerFactory.getLogger(InitialInventoryImportRunner.class);

        private final InitialInventoryImportProperties properties;
        private final ImportInitialInventorySnapshot importer;

        @Override
        public void run(org.springframework.boot.ApplicationArguments arguments) throws Exception {
            properties.validateEnabledConfiguration();
            try {
                log(importer.execute(properties.mode(), properties.file(), properties.effectiveDate()));
            } catch (InitialInventoryImportException exception) {
                log(exception.report());
                throw exception;
            }
        }

        private void log(InitialInventoryImportReport report) {
            LOGGER.info(
                "Initial inventory import: mode={}, effectiveDate={}, rows={}, catalogOnly={}, openingStock={}, rejected={}",
                report.mode(), report.effectiveDate(), report.totalRowCount(), report.catalogOnlyCount(),
                report.openingStockCount(), report.rejectedCount()
            );
            report.rows().forEach(row -> LOGGER.info(
                "row={} name={} gender={} essenceReference={} productionTypeCode={} lotCode={} quantity={} expiration={} "
                    + "outcome={} validationCode={} reason={}",
                row.sourceRowNumber(), row.catalogName(), row.gender(), row.essenceReference(), row.productionTypeCode(),
                row.lotCode(), row.quantity(), row.expiration(), row.outcome(), row.validationCode(), row.validationReason()
            ));
        }
    }
}
