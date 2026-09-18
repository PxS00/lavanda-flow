package com.ceudelavanda.lavandaflow.inventory.infrastructure.web;

import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.inventory.application.overview.GetInventoryStockList;
import com.ceudelavanda.lavandaflow.inventory.application.overview.InventoryStockListQuery;
import com.ceudelavanda.lavandaflow.inventory.application.overview.InventoryStockListResult;
import com.ceudelavanda.lavandaflow.inventory.infrastructure.config.InventoryAlertProperties;
import com.ceudelavanda.lavandaflow.shared.config.ClockConfig;
import com.ceudelavanda.lavandaflow.shared.config.ExactDecimalJsonConfiguration;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InventoryStockListController.class)
@Import({ClockConfig.class, ExactDecimalJsonConfiguration.class})
class InventoryStockListControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private GetInventoryStockList getInventoryStockList;
    @MockitoBean private InventoryAlertProperties inventoryAlertProperties;

    @Test
    @WithMockUser
    void shouldReturnPageForRepeatedCategoriesWithoutCsrf() throws Exception {
        var id = UUID.randomUUID();
        when(inventoryAlertProperties.expirationWindowDays()).thenReturn(30);
        when(getInventoryStockList.execute(new InventoryStockListQuery(
            List.of("BASE", "ALCOHOL"), 1, 20, 30
        ))).thenReturn(new InventoryStockListResult(
            List.of(new InventoryStockListResult.Entry(
                id, "Álcool", "ALCOHOL", UnitOfMeasure.LITER, true, null, "INPUT",
                new BigDecimal("12.500000"), new BigDecimal("10.000000"), new BigDecimal("8.000000"),
                false, false, 2, LocalDate.of(2026, 9, 30)
            )), 1, 20, 21, 2, LocalDate.of(2026, 9, 18), 30
        ));

        mockMvc.perform(get("/api/v1/inventory/items")
                .queryParam("category", "BASE", "ALCOHOL")
                .queryParam("page", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].inventoryItemId").value(id.toString()))
            .andExpect(jsonPath("$.content[0].totalCurrentQuantity").value("12.500000"))
            .andExpect(jsonPath("$.content[0].productionTypeCode").value("INPUT"))
            .andExpect(jsonPath("$.asOfDate").value("2026-09-18"))
            .andExpect(jsonPath("$.expirationWindowDays").value(30))
            .andExpect(jsonPath("$.page").value(1));
    }

    @Test
    void shouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/items"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void shouldReturnStandardBadRequestForInvalidPagination() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/items").queryParam("size", "101"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INVENTORY_STOCK_LIST_QUERY"));
    }

    @Test
    @WithMockUser
    void shouldSupportAllStockAndOneCategory() throws Exception {
        when(inventoryAlertProperties.expirationWindowDays()).thenReturn(30);
        var empty = new InventoryStockListResult(
            List.of(), 0, 20, 0, 0, LocalDate.of(2026, 9, 18), 30
        );
        when(getInventoryStockList.execute(new InventoryStockListQuery(List.of(), 0, 20, 30)))
            .thenReturn(empty);
        when(getInventoryStockList.execute(new InventoryStockListQuery(List.of("ESSENCE"), 0, 20, 30)))
            .thenReturn(empty);

        mockMvc.perform(get("/api/v1/inventory/items")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/inventory/items").queryParam("category", "ESSENCE"))
            .andExpect(status().isOk());
    }

}
