package com.ceudelavanda.lavandaflow.inventory.infrastructure.web;

import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.inventory.application.overview.GetInventoryItemOverview;
import com.ceudelavanda.lavandaflow.inventory.application.overview.GetInventoryItemOverviewQuery;
import com.ceudelavanda.lavandaflow.inventory.application.overview.InventoryItemOverviewResult;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InventoryItemNotFoundException;
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
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InventoryItemOverviewController.class)
@Import(ClockConfig.class)
@WithMockUser
class InventoryItemOverviewControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private GetInventoryItemOverview getInventoryItemOverview;
    @MockitoBean private InventoryAlertProperties inventoryAlertProperties;

    @Test
    void shouldReturnOperationalOverview() throws Exception {
        var id = UUID.randomUUID();
        when(inventoryAlertProperties.expirationWindowDays()).thenReturn(30);
        when(getInventoryItemOverview.execute(new GetInventoryItemOverviewQuery(id, 30)))
            .thenReturn(new InventoryItemOverviewResult(
                id, "Lavender Essence", "ESSENCE", UnitOfMeasure.MILLILITER, true,
                LocalDate.of(2026, 8, 31), 30,
                new BigDecimal("20.000000"), new BigDecimal("8.000000"), new BigDecimal("10.000000"),
                true, false, 3, LocalDate.of(2026, 9, 5), 1, 1
            ));

        mockMvc.perform(get("/api/v1/inventory/items/{inventoryItemId}/overview", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.inventoryItemId").value(id.toString()))
            .andExpect(jsonPath("$.name").value("Lavender Essence"))
            .andExpect(jsonPath("$.category").value("ESSENCE"))
            .andExpect(jsonPath("$.unitOfMeasure").value("MILLILITER"))
            .andExpect(jsonPath("$.totalCurrentQuantity").value(20.0))
            .andExpect(jsonPath("$.availableQuantity").value(8.0))
            .andExpect(jsonPath("$.minimumQuantity").value(10.0))
            .andExpect(jsonPath("$.lowStock").value(true))
            .andExpect(jsonPath("$.outOfStock").value(false))
            .andExpect(jsonPath("$.nonZeroBatchCount").value(3))
            .andExpect(jsonPath("$.nearestExpiration").value("2026-09-05"))
            .andExpect(jsonPath("$.expiredBatchCount").value(1))
            .andExpect(jsonPath("$.expiringSoonBatchCount").value(1));
    }

    @Test
    void shouldReturnStandardizedNotFoundForUnknownItem() throws Exception {
        var id = UUID.randomUUID();
        when(inventoryAlertProperties.expirationWindowDays()).thenReturn(30);
        when(getInventoryItemOverview.execute(new GetInventoryItemOverviewQuery(id, 30)))
            .thenThrow(new InventoryItemNotFoundException(id));

        mockMvc.perform(get("/api/v1/inventory/items/{inventoryItemId}/overview", id))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("INVENTORY_ITEM_NOT_FOUND"));
    }

    @Test
    void shouldReturnStandardizedBadRequestForMalformedUuid() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/items/{inventoryItemId}/overview", "not-a-uuid"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST_PARAMETER"));
    }
}
