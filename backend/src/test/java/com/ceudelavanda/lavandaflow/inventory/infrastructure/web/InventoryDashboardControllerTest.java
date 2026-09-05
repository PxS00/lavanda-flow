package com.ceudelavanda.lavandaflow.inventory.infrastructure.web;

import com.ceudelavanda.lavandaflow.inventory.application.dashboard.GetOperationalDashboard;
import com.ceudelavanda.lavandaflow.inventory.application.dashboard.OperationalDashboardSummary;
import com.ceudelavanda.lavandaflow.inventory.infrastructure.config.InventoryAlertProperties;
import com.ceudelavanda.lavandaflow.shared.config.ClockConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InventoryDashboardController.class)
@Import(ClockConfig.class)
@WithMockUser
class InventoryDashboardControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private GetOperationalDashboard getOperationalDashboard;
    @MockitoBean private InventoryAlertProperties inventoryAlertProperties;

    @Test
    void shouldReturnTheStableDashboardContractUsingTheConfiguredWindow() throws Exception {
        when(inventoryAlertProperties.expirationWindowDays()).thenReturn(30);
        when(getOperationalDashboard.execute(30)).thenReturn(new OperationalDashboardSummary(
            LocalDate.of(2000, 1, 10), 30, 6, 3, 2, 4, 5
        ));

        mockMvc.perform(get("/api/v1/inventory/dashboard"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", aMapWithSize(7)))
            .andExpect(jsonPath("$.asOfDate").value("2000-01-10"))
            .andExpect(jsonPath("$.expirationWindowDays").value(30))
            .andExpect(jsonPath("$.activeItemCount").value(6))
            .andExpect(jsonPath("$.lowStockItemCount").value(3))
            .andExpect(jsonPath("$.outOfStockItemCount").value(2))
            .andExpect(jsonPath("$.expiringSoonBatchCount").value(4))
            .andExpect(jsonPath("$.expiredBatchCount").value(5));

        verify(getOperationalDashboard).execute(30);
    }
}
