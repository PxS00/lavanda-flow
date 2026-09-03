package com.ceudelavanda.lavandaflow.production.application.genealogy;

/** Distinguishes production-owned outputs from batches with no producing execution. */
public enum GenealogyBatchOrigin {
    INTERNALLY_PRODUCED,
    EXTERNAL_OR_NON_PRODUCED
}
