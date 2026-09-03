package com.ceudelavanda.lavandaflow.production.application.genealogy;

/** Supported recursive traversal directions from one inventory batch. */
public enum GenealogyDirection {
    UPSTREAM,
    DOWNSTREAM,
    BOTH;

    boolean includesUpstream() {
        return this == UPSTREAM || this == BOTH;
    }

    boolean includesDownstream() {
        return this == DOWNSTREAM || this == BOTH;
    }
}
