package com.ceudelavanda.lavandaflow.catalog.infrastructure.web;

import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.catalog.application.GetInventoryItem;
import com.ceudelavanda.lavandaflow.catalog.application.InventoryItemNotFoundException;
import com.ceudelavanda.lavandaflow.catalog.application.InventoryItemPage;
import com.ceudelavanda.lavandaflow.catalog.application.InventoryItemResult;
import com.ceudelavanda.lavandaflow.catalog.application.InventoryItemSearchQuery;
import com.ceudelavanda.lavandaflow.catalog.application.RegisterInventoryItem;
import com.ceudelavanda.lavandaflow.catalog.application.RegisterInventoryItemCommand;
import com.ceudelavanda.lavandaflow.catalog.application.SearchInventoryItems;
import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import com.ceudelavanda.lavandaflow.shared.config.ClockConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InventoryItemController.class)
@Import(ClockConfig.class)
@WithMockUser
class InventoryItemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegisterInventoryItem registerInventoryItem;

    @MockitoBean
    private GetInventoryItem getInventoryItem;

    @MockitoBean
    private SearchInventoryItems searchInventoryItems;

    @Test
    void shouldRegisterInventoryItem() throws Exception {
        var itemId = UUID.randomUUID();
        var command = new RegisterInventoryItemCommand(
            "Lavender Essence", "Floral raw material", Category.ESSENCE, UnitOfMeasure.MILLILITER, "014", "ESS"
        );
        when(registerInventoryItem.execute(command)).thenReturn(new InventoryItemResult(
            itemId, "Lavender Essence", "Floral raw material", Category.ESSENCE, UnitOfMeasure.MILLILITER, true, "014", "ESS"
        ));

        mockMvc.perform(post("/api/v1/inventory-items")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Lavender Essence",
                      "description": "Floral raw material",
                      "category": "ESSENCE",
                      "unitOfMeasure": "MILLILITER",
                      "essenceReference": "014",
                      "productionTypeCode": "ESS"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/v1/inventory-items/" + itemId))
            .andExpect(jsonPath("$.id").value(itemId.toString()))
            .andExpect(jsonPath("$.name").value("Lavender Essence"))
            .andExpect(jsonPath("$.category").value("ESSENCE"))
            .andExpect(jsonPath("$.unitOfMeasure").value("MILLILITER"))
            .andExpect(jsonPath("$.essenceReference").value("014"))
            .andExpect(jsonPath("$.productionTypeCode").value("ESS"))
            .andExpect(jsonPath("$.active").value(true));

        verify(registerInventoryItem).execute(command);
    }

    @Test
    void shouldAcceptExistingRegistrationRequestWithoutProductionMetadata() throws Exception {
        var itemId = UUID.randomUUID();
        var command = new RegisterInventoryItemCommand(
            "Lavender Essence", "Floral raw material", Category.ESSENCE, UnitOfMeasure.MILLILITER
        );
        when(registerInventoryItem.execute(command)).thenReturn(new InventoryItemResult(
            itemId, "Lavender Essence", "Floral raw material", Category.ESSENCE, UnitOfMeasure.MILLILITER, true
        ));

        mockMvc.perform(post("/api/v1/inventory-items")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Lavender Essence",
                      "description": "Floral raw material",
                      "category": "ESSENCE",
                      "unitOfMeasure": "MILLILITER"
                    }
                    """))
            .andExpect(status().isCreated());

        verify(registerInventoryItem).execute(command);
    }

    @Test
    void shouldReturnValidationErrorForBlankName() throws Exception {
        mockMvc.perform(post("/api/v1/inventory-items")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": " ",
                      "category": "ESSENCE",
                      "unitOfMeasure": "MILLILITER"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.details.name").exists());
    }

    @Test
    void shouldRejectInvalidProductionReferenceFormat() throws Exception {
        mockMvc.perform(post("/api/v1/inventory-items")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Lavender Essence",
                      "category": "ESSENCE",
                      "unitOfMeasure": "MILLILITER",
                      "essenceReference": "000",
                      "productionTypeCode": "ess"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.details.essenceReference").exists())
            .andExpect(jsonPath("$.details.productionTypeCode").exists());
    }

    @Test
    void shouldRetrieveInventoryItem() throws Exception {
        var itemId = UUID.randomUUID();
        when(getInventoryItem.execute(itemId)).thenReturn(new InventoryItemResult(
            itemId, "Lavender Essence", null, Category.ESSENCE, UnitOfMeasure.MILLILITER, true
        ));

        mockMvc.perform(get("/api/v1/inventory-items/{inventoryItemId}", itemId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(itemId.toString()))
            .andExpect(jsonPath("$.name").value("Lavender Essence"));
    }

    @Test
    void shouldReturnStandardizedNotFoundError() throws Exception {
        var itemId = UUID.randomUUID();
        when(getInventoryItem.execute(itemId)).thenThrow(new InventoryItemNotFoundException(itemId));

        mockMvc.perform(get("/api/v1/inventory-items/{inventoryItemId}", itemId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("INVENTORY_ITEM_NOT_FOUND"))
            .andExpect(jsonPath("$.details.inventoryItemId").value(itemId.toString()));
    }

    @Test
    void shouldExposeFilteredPaginatedSearch() throws Exception {
        var itemId = UUID.randomUUID();
        var query = new InventoryItemSearchQuery("lavender", Category.ESSENCE, true, 1, 5);
        when(searchInventoryItems.execute(query)).thenReturn(new InventoryItemPage(
            List.of(new InventoryItemResult(
                itemId, "Lavender Essence", null, Category.ESSENCE, UnitOfMeasure.MILLILITER, true
            )),
            1, 5, 6, 2
        ));

        mockMvc.perform(get("/api/v1/inventory-items")
                .param("name", "lavender")
                .param("category", "ESSENCE")
                .param("active", "true")
                .param("page", "1")
                .param("size", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.size").value(5))
            .andExpect(jsonPath("$.totalElements").value(6))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.content[0].id").value(itemId.toString()));

        verify(searchInventoryItems).execute(query);
    }

    @Test
    void shouldRejectInvalidPaginationAndBinding() throws Exception {
        mockMvc.perform(get("/api/v1/inventory-items").param("size", "101"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INVENTORY_ITEM_SEARCH_QUERY"));

        mockMvc.perform(get("/api/v1/inventory-items").param("category", "UNKNOWN"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST_PARAMETER"));
    }
}
