package com.ceudelavanda.lavandaflow.sales.application;

import java.time.LocalDate;
import java.util.UUID;

/** Draft search by optional customer and UTC creation dates (inclusive), newest first then UUID.
 * q is a literal partial match on the stable order UUID; blank lists normally. */
public record OrderSearchQuery(String q, UUID customerId, LocalDate from, LocalDate to, int page, int size) {
    public OrderSearchQuery {
        q = q == null || q.isBlank() ? null : q.trim();
        if (q != null && q.length() > 36) throw OrderException.search("q", "must have at most 36 characters");
        if (page < 0) throw OrderException.search("page", "must be zero or positive");
        if (size < 1 || size > 100) throw OrderException.search("size", "must be between 1 and 100");
        if ((long) page * size > Integer.MAX_VALUE) throw OrderException.search("page", "page offset exceeds supported range");
        if (from != null && to != null && from.isAfter(to)) throw OrderException.search("to", "must not precede from");
        if (to != null && to.equals(LocalDate.MAX)) throw OrderException.search("to", "date out of range");
    }
}
