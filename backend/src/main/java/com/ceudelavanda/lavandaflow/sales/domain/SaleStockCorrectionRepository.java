package com.ceudelavanda.lavandaflow.sales.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Correction persistence; writers must hold the sale row lock before reading limits or inserting. */
public interface SaleStockCorrectionRepository {
    Optional<SaleStockCorrection> find(UUID saleId, UUID id);
    List<SaleStockCorrection> findBySaleId(UUID saleId);
    void insert(SaleStockCorrection correction);
}
