package com.ceudelavanda.lavandaflow.inventory.infrastructure.initialsnapshot;

import com.ceudelavanda.lavandaflow.inventory.application.initialsnapshot.InitialInventoryImportMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.LocalDate;

/** Typed, opt-in configuration for the one-time offline inventory import runner. */
@ConfigurationProperties(prefix = "lavanda.inventory.initial-import")
public record InitialInventoryImportProperties(
    boolean enabled,
    InitialInventoryImportMode mode,
    Path file,
    LocalDate effectiveDate
) {
    void validateEnabledConfiguration() {
        if (mode == null || file == null || effectiveDate == null) {
            throw new IllegalStateException(
                "Initial inventory import requires mode, file and effective-date when enabled"
            );
        }
    }
}
