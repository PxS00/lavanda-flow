package com.ceudelavanda.lavandaflow.production.application.history;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GetProductionExecutionHistoryQueryTest {

    @Test
    void shouldAcceptInclusiveSingleDayRange() {
        var date = LocalDate.of(2026, 9, 18);

        var query = new GetProductionExecutionHistoryQuery(date, date, 0, 100);

        assertThat(query.from()).isEqualTo(date);
        assertThat(query.to()).isEqualTo(date);
    }

    @Test
    void shouldRejectInvalidPaginationAndDateRange() {
        assertInvalid(null, null, -1, 20, "page");
        assertInvalid(null, null, 0, 0, "size");
        assertInvalid(null, null, 0, 101, "size");
        assertInvalid(
            LocalDate.of(2026, 9, 19), LocalDate.of(2026, 9, 18), 0, 20, "dateRange"
        );
    }

    private static void assertInvalid(
        LocalDate from,
        LocalDate to,
        int page,
        int size,
        String detail
    ) {
        assertThatThrownBy(() -> new GetProductionExecutionHistoryQuery(from, to, page, size))
            .isInstanceOf(InvalidProductionExecutionHistoryQueryException.class)
            .satisfies(exception -> assertThat(
                ((InvalidProductionExecutionHistoryQueryException) exception).getDetails()
            ).containsKey(detail));
    }
}
