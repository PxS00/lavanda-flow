package com.ceudelavanda.lavandaflow.inventory.infrastructure.web;

import com.ceudelavanda.lavandaflow.inventory.application.GetExpirationAlerts;
import com.ceudelavanda.lavandaflow.inventory.application.query.GetExpirationAlertsQuery;
import com.ceudelavanda.lavandaflow.inventory.application.result.ExpirationAlertEntryResult;
import com.ceudelavanda.lavandaflow.inventory.application.result.ExpirationAlertStatus;
import com.ceudelavanda.lavandaflow.inventory.application.result.ExpirationAlertsResult;
import com.ceudelavanda.lavandaflow.inventory.infrastructure.config.InventoryAlertProperties;
import com.ceudelavanda.lavandaflow.shared.config.ClockConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InventoryAlertController.class)
@Import(ClockConfig.class)
@WithMockUser
class InventoryAlertControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetExpirationAlerts getExpirationAlerts;

    @MockitoBean
    private InventoryAlertProperties inventoryAlertProperties;

    @Test
    void shouldReturnExpirationAlertsUsingConfiguredDefaultWindow() throws Exception {
        var itemId = UUID.randomUUID();
        var expiredBatchId = UUID.randomUUID();
        var expiringBatchId = UUID.randomUUID();
        var asOfDate = LocalDate.of(2026, 8, 26);
        when(inventoryAlertProperties.expirationWindowDays()).thenReturn(30);
        when(getExpirationAlerts.execute(any(GetExpirationAlertsQuery.class))).thenReturn(
            new ExpirationAlertsResult(
                asOfDate,
                30,
                List.of(
                    new ExpirationAlertEntryResult(
                        itemId,
                        expiredBatchId,
                        "EXPIRED",
                        new BigDecimal("25.500000"),
                        asOfDate,
                        0,
                        ExpirationAlertStatus.EXPIRED
                    ),
                    new ExpirationAlertEntryResult(
                        itemId,
                        expiringBatchId,
                        "SOON",
                        new BigDecimal("40.000000"),
                        asOfDate.plusDays(8),
                        8,
                        ExpirationAlertStatus.EXPIRING_SOON
                    )
                )
            )
        );

        mockMvc.perform(get("/api/v1/inventory/alerts/expiration"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.asOfDate").value("2026-08-26"))
            .andExpect(jsonPath("$.windowDays").value(30))
            .andExpect(jsonPath("$.alerts[0].inventoryItemId").value(itemId.toString()))
            .andExpect(jsonPath("$.alerts[0].batchId").value(expiredBatchId.toString()))
            .andExpect(jsonPath("$.alerts[0].lotCode").value("EXPIRED"))
            .andExpect(jsonPath("$.alerts[0].currentQuantity").value(25.500000))
            .andExpect(jsonPath("$.alerts[0].expiresAt").value("2026-08-26"))
            .andExpect(jsonPath("$.alerts[0].daysUntilExpiration").value(0))
            .andExpect(jsonPath("$.alerts[0].status").value("EXPIRED"))
            .andExpect(jsonPath("$.alerts[1].batchId").value(expiringBatchId.toString()))
            .andExpect(jsonPath("$.alerts[1].daysUntilExpiration").value(8))
            .andExpect(jsonPath("$.alerts[1].status").value("EXPIRING_SOON"));

        var captor = org.mockito.ArgumentCaptor.forClass(GetExpirationAlertsQuery.class);
        verify(getExpirationAlerts).execute(captor.capture());
        assertThat(captor.getValue().windowDays()).isEqualTo(30);
        verify(inventoryAlertProperties).expirationWindowDays();
    }

    @Test
    void shouldUseExplicitWindowOverride() throws Exception {
        when(getExpirationAlerts.execute(any(GetExpirationAlertsQuery.class))).thenReturn(
            new ExpirationAlertsResult(LocalDate.of(2026, 8, 26), 60, List.of())
        );

        mockMvc.perform(get("/api/v1/inventory/alerts/expiration")
                .queryParam("windowDays", "60"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.windowDays").value(60))
            .andExpect(jsonPath("$.alerts").isEmpty());

        var captor = org.mockito.ArgumentCaptor.forClass(GetExpirationAlertsQuery.class);
        verify(getExpirationAlerts).execute(captor.capture());
        assertThat(captor.getValue().windowDays()).isEqualTo(60);
        verify(inventoryAlertProperties, never()).expirationWindowDays();
    }

    @Test
    void shouldReturnBadRequestForNegativeWindow() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/alerts/expiration")
                .queryParam("windowDays", "-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.code").value("INVALID_EXPIRATION_ALERT_WINDOW"))
            .andExpect(jsonPath("$.message")
                .value("Expiration alert window must be zero or positive: -1"))
            .andExpect(jsonPath("$.path").value("/api/v1/inventory/alerts/expiration"))
            .andExpect(jsonPath("$.timestamp").exists());

        verify(getExpirationAlerts, never()).execute(any(GetExpirationAlertsQuery.class));
    }
}
