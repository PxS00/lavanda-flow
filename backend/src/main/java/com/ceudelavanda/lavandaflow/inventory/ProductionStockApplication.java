package com.ceudelavanda.lavandaflow.inventory;

/**
 * Public inventory application contract for the stock effects of one production execution.
 *
 * <p>The caller supplies exact source batches; inventory remains authoritative for validation,
 * locking, balances, movements, and output-batch creation.</p>
 */
public interface ProductionStockApplication {

    ProductionStockResult apply(ProductionStockCommand command);
}
