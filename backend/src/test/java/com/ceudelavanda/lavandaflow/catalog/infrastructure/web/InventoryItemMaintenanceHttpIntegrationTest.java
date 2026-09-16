package com.ceudelavanda.lavandaflow.catalog.infrastructure.web;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.catalog.application.RegisterInventoryItem;
import com.ceudelavanda.lavandaflow.catalog.application.RegisterInventoryItemCommand;
import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest
@WithMockUser
class InventoryItemMaintenanceHttpIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegisterInventoryItem registerInventoryItem;

    @Test
    void shouldPersistStableAssignmentAndAcceptItsHttpReplay() throws Exception {
        var item = registerInventoryItem.execute(new RegisterInventoryItemCommand(
            "Issue222 HTTP Assignment", null, Category.ESSENCE, UnitOfMeasure.MILLILITER
        ));
        var body = maintenanceRequest("Issue222 HTTP Assignment", "226", null);

        mockMvc.perform(put("/api/v1/inventory-items/{inventoryItemId}", item.id())
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(item.id().toString()))
            .andExpect(jsonPath("$.essenceReference").value("226"));

        mockMvc.perform(put("/api/v1/inventory-items/{inventoryItemId}", item.id())
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.essenceReference").value("226"));
    }

    @Test
    void shouldReturnStructuredConflictForDuplicateCanonicalEssenceReference() throws Exception {
        registerInventoryItem.execute(new RegisterInventoryItemCommand(
            "Issue222 HTTP Canonical Essence", null, Category.ESSENCE, UnitOfMeasure.MILLILITER, "225", null
        ));
        var candidate = registerInventoryItem.execute(new RegisterInventoryItemCommand(
            "Issue222 HTTP Candidate Essence", null, Category.ESSENCE, UnitOfMeasure.MILLILITER
        ));

        mockMvc.perform(put("/api/v1/inventory-items/{inventoryItemId}", candidate.id())
                .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(maintenanceRequest("Issue222 HTTP Candidate Essence", "225", null)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INVENTORY_ITEM_CANONICAL_ESSENCE_REFERENCE_CONFLICT"))
            .andExpect(jsonPath("$.details.essenceReference").exists());
    }

    private static String maintenanceRequest(String name, String essenceReference, String productionTypeCode) {
        return """
            {"name":"%s","description":null,"active":true,
             "essenceReference":"%s","productionTypeCode":%s}
            """.formatted(
            name,
            essenceReference,
            productionTypeCode == null ? "null" : "\"%s\"".formatted(productionTypeCode)
        );
    }
}
