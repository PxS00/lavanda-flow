package com.ceudelavanda.lavandaflow.inventory.application.history;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GetMovementHistoryQueryTest {

    @Test
    void shouldAcceptValidPaginationAndHalfOpenDateRange() {
        var from = Instant.parse("2026-08-01T00:00:00Z");
        var to = Instant.parse("2026-09-01T00:00:00Z");

        var query = new GetMovementHistoryQuery(null, null, null, from, to, 0, 100);

        assertThat(query.from()).isEqualTo(from);
        assertThat(query.to()).isEqualTo(to);
        assertThat(query.page()).isZero();
        assertThat(query.size()).isEqualTo(100);
    }

    @Test
    void shouldRejectNegativePage() {
        assertThatThrownBy(() -> new GetMovementHistoryQuery(null, null, null, null, null, -1, 20))
            .isInstanceOf(InvalidMovementHistoryQueryException.class)
            .satisfies(exception -> assertThat(((InvalidMovementHistoryQueryException) exception).getDetails())
                .containsEntry("page", "must be zero or positive"));
    }

    @Test
    void shouldRejectPageSizeOutsideSupportedRange() {
        assertThatThrownBy(() -> new GetMovementHistoryQuery(null, null, null, null, null, 0, 0))
            .isInstanceOf(InvalidMovementHistoryQueryException.class);

        assertThatThrownBy(() -> new GetMovementHistoryQuery(null, null, null, null, null, 0, 101))
            .isInstanceOf(InvalidMovementHistoryQueryException.class);
    }

    @Test
    void shouldRejectEmptyOrReversedDateRange() {
        var instant = Instant.parse("2026-08-27T12:00:00Z");

        assertThatThrownBy(() -> new GetMovementHistoryQuery(null, null, null, instant, instant, 0, 20))
            .isInstanceOf(InvalidMovementHistoryQueryException.class)
            .satisfies(exception -> assertThat(((InvalidMovementHistoryQueryException) exception).getDetails())
                .containsEntry("dateRange", "from must be earlier than to"));

        assertThatThrownBy(() -> new GetMovementHistoryQuery(
            null,
            null,
            null,
            instant.plusSeconds(1),
            instant,
            0,
            20
        )).isInstanceOf(InvalidMovementHistoryQueryException.class);
    }
}
