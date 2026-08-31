package com.ceudelavanda.lavandaflow.inventory.domain;

import com.ceudelavanda.lavandaflow.inventory.domain.exception.InvalidBatchDataException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BatchReceiptInvariantTest {

    private static final UUID ITEM_ID = UUID.randomUUID();
    private static final LocalDate RECEIVED_AT = LocalDate.of(2026, 8, 31);

    @Test
    void shouldNormalizeOptionalLotCode() {
        var batch = Batch.create(
            ITEM_ID,
            null,
            "  LOT-96  ",
            BigDecimal.ONE,
            RECEIVED_AT,
            null
        );

        assertThat(batch.getLotCode()).isEqualTo("LOT-96");
    }

    @Test
    void shouldRejectLotCodeLongerThanPersistenceContract() {
        assertThatThrownBy(() -> Batch.create(
            ITEM_ID,
            null,
            "L".repeat(256),
            BigDecimal.ONE,
            RECEIVED_AT,
            null
        ))
            .isInstanceOf(InvalidBatchDataException.class)
            .satisfies(exception -> assertThat(((InvalidBatchDataException) exception).getCode())
                .isEqualTo("INVALID_BATCH_DATA"));
    }

    @Test
    void shouldRejectExpirationBeforeReceivedDate() {
        assertThatThrownBy(() -> Batch.create(
            ITEM_ID,
            null,
            "LOT-96",
            BigDecimal.ONE,
            RECEIVED_AT,
            RECEIVED_AT.minusDays(1)
        ))
            .isInstanceOf(InvalidBatchDataException.class)
            .hasMessage("expiresAt must not be before receivedAt");
    }

    @Test
    void shouldAllowExpirationOnReceivedDate() {
        var batch = Batch.create(
            ITEM_ID,
            null,
            "LOT-96",
            BigDecimal.ONE,
            RECEIVED_AT,
            RECEIVED_AT
        );

        assertThat(batch.getExpiresAt()).isEqualTo(RECEIVED_AT);
    }
}
