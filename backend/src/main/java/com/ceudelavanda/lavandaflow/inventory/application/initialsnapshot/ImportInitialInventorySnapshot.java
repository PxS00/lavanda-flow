package com.ceudelavanda.lavandaflow.inventory.application.initialsnapshot;

import com.ceudelavanda.lavandaflow.catalog.InventoryItemLookup;
import com.ceudelavanda.lavandaflow.catalog.InventoryItemRegistration;
import com.ceudelavanda.lavandaflow.inventory.application.receipt.RegisterStockReceipt;
import com.ceudelavanda.lavandaflow.inventory.application.receipt.RegisterStockReceiptCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;

/** Inventory-owned one-time importer for the initial operational snapshot defined by issue #134. */
@Service
@RequiredArgsConstructor
public class ImportInitialInventorySnapshot {

    private static final String RECEIPT_REASON = "Importação inicial do estoque";

    private final InitialInventorySnapshotParser parser;
    private final InventoryItemLookup inventoryItemLookup;
    private final InventoryItemRegistration inventoryItemRegistration;
    private final RegisterStockReceipt registerStockReceipt;

    /**
     * Fully validates the external file, then either reports without writes or atomically applies it.
     * APPLY requires an empty catalog and creates opening batches only through the stock-receipt use case.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public InitialInventoryImportReport execute(
        InitialInventoryImportMode mode,
        Path file,
        LocalDate effectiveDate
    ) throws IOException {
        var plan = parser.parse(file, mode, effectiveDate);
        var report = plan.report();

        if (mode == InitialInventoryImportMode.DRY_RUN) {
            return report;
        }
        if (report.rejectedCount() > 0) {
            throw new InitialInventoryImportException("Initial inventory snapshot contains rejected rows", report);
        }
        if (inventoryItemLookup.existsAny()) {
            throw new InitialInventoryImportException("Operational catalog is already initialized", report);
        }

        for (var row : plan.validRows()) {
            var item = inventoryItemRegistration.registerEssence(row.catalogName());
            if (row.quantity().signum() > 0) {
                registerStockReceipt.execute(new RegisterStockReceiptCommand(
                    item.id(),
                    null,
                    null,
                    row.quantity(),
                    effectiveDate,
                    row.expiration(),
                    RECEIPT_REASON
                ));
            }
        }
        return report;
    }
}
