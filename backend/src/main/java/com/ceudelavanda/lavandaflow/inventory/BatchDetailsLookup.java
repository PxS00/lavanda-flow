package com.ceudelavanda.lavandaflow.inventory;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Public inventory contract for bulk immutable batch display details. */
public interface BatchDetailsLookup {

    /** Missing batch identifiers are omitted from the returned immutable values. */
    List<BatchDetails> findByIds(Collection<UUID> batchIds);
}
