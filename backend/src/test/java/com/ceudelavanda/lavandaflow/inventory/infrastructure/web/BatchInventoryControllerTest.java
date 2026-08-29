package com.ceudelavanda.lavandaflow.inventory.infrastructure.web;

import com.ceudelavanda.lavandaflow.inventory.application.batch.BatchInventoryEntryResult;
import com.ceudelavanda.lavandaflow.inventory.application.batch.BatchInventoryResult;
import com.ceudelavanda.lavandaflow.inventory.application.batch.BatchOperationalStatus;
import com.ceudelavanda.lavandaflow.inventory.application.batch.GetBatchInventory;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InventoryItemNotFoundException;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BatchInventoryController.class)
@Import(ClockConfig.class)
@WithMockUser
class BatchInventoryControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private GetBatchInventory getBatchInventory;

    @Test
    void shouldReturnOperationalBatchInventory() throws Exception {
        var itemId = UUID.randomUUID();
        var supplierId = UUID.randomUUID();
        var batchId = UUID.randomUUID();
        when(getBatchInventory.execute(itemId)).thenReturn(new BatchInventoryResult(
            itemId,
            LocalDate.of(2026, 8, 28),
            List.of(new BatchInventoryEntryResult(
                batchId,
                itemId,
                supplierId,
                "LOT-95",
                new BigDecimal("100.000000"),
                new BigDecimal("30.500000"),
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 9, 15),
                BatchOperationalStatus.AVAILABLE
            ))
        ));

        mockMvc.perform(get("/api/v1/inventory/items/{inventoryItemId}/batches", itemId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.inventoryItemId").value(itemId.toString()))
            .andExpect(jsonPath("$.asOfDate").value("2026-08-28"))
            .andExpect(jsonPath("$.batches[0].batchId").value(batchId.toString()))
            .andExpect(jsonPath("$.batches[0].supplierId").value(supplierId.toString()))
            .andExpect(jsonPath("$.batches[0].lotCode").value("LOT-95"))
            .andExpect(jsonPath("$.batches[0].initialQuantity").value(100.0))
            .andExpect(jsonPath("$.batches[0].currentQuantity").value(30.5))
            .andExpect(jsonPath("$.batches[0].status").value("AVAILABLE"));
    }

    @Test
    void shouldReturnEmptyListForExistingItemWithoutBatches() throws Exception {
        var itemId = UUID.randomUUID();
        when(getBatchInventory.execute(itemId)).thenReturn(new BatchInventoryResult(
            itemId,
            LocalDate.of(2026, 8, 28),
            List.of()
        ));

        mockMvc.perform(get("/api/v1/inventory/items/{inventoryItemId}/batches", itemId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.batches").isEmpty());
    }

    @Test
    void shouldReturnStandardizedNotFoundErrorForUnknownItem() throws Exception {
        var itemId = UUID.randomUUID();
        when(getBatchInventory.execute(itemId)).thenThrow(new InventoryItemNotFoundException(itemId));

        mockMvc.perform(get("/api/v1/inventory/items/{inventoryItemId}/batches", itemId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("INVENTORY_ITEM_NOT_FOUND"));
    }
}
