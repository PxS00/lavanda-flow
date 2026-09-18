package com.ceudelavanda.lavandaflow.inventory.infrastructure.web;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest
@WithMockUser
class InventoryStockListHttpIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void shouldReturnStandardBadRequestForUnknownCatalogCategory() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/items").queryParam("category", "UNKNOWN"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INVENTORY_ITEM_SEARCH_QUERY"))
            .andExpect(jsonPath("$.details.category").value("must be a valid category"));
    }
}
