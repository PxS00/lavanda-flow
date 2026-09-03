package com.ceudelavanda.lavandaflow.production.domain;

/** Persistence boundary for completed production executions and their exact consumptions. */
public interface ProductionExecutionRepository {

    ProductionExecution save(ProductionExecution execution);
}
