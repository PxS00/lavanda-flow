package com.ceudelavanda.lavandaflow.shared.config;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class ClockConfigTest {

    @Test
    void shouldConfigureClockForSaoPauloBusinessDate() {
        var clock = new ClockConfig().clock();

        assertThat(clock.getZone()).isEqualTo(ZoneId.of("America/Sao_Paulo"));
        assertThat(clock.instant()).isNotNull();
    }
}
