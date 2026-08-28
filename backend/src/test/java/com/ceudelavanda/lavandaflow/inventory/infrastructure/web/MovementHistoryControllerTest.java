package com.ceudelavanda.lavandaflow.inventory.infrastructure.web;

import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.inventory.application.history.GetMovementHistory;
import com.ceudelavanda.lavandaflow.inventory.application.history.GetMovementHistoryQuery;
import com.ceudelavanda.lavandaflow.inventory.application.history.MovementHistoryEntryResult;
import com.ceudelavanda.lavandaflow.inventory.application.history.MovementHistoryResult;
import com.ceudelavanda.lavandaflow.inventory.domain.MovementType;
import com.ceudelavanda.lavandaflow.shared.config.ClockConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MovementHistoryController.class)
@Import(ClockConfig.class)
@WithMockUser
class MovementHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetMovementHistory getMovementHistory;

    @Test
    void shouldExposeFilteredPaginatedMovementHistory() throws Exception {
        var itemId = UUID.randomUUID();
        var batchId = UUID.randomUUID();
        var movementId = UUID.randomUUID();
        var from = Instant.parse("2026-08-01T00:00:00Z");
        var to = Instant.parse("2026-09-01T00:00:00Z");
        var occurredAt = Instant.parse("2026-08-27T14:30:00Z");
        var query = new GetMovementHistoryQuery(
            itemId,
            batchId,
            MovementType.CONSUMPTION,
            from,
            to,
            1,
            5
        );
        var result = new MovementHistoryResult(
            List.of(new MovementHistoryEntryResult(
                movementId,
                itemId,
                "Good Girl Essence",
                UnitOfMeasure.MILLILITER,
                true,
                batchId,
                "GG-01",
                MovementType.CONSUMPTION,
                new BigDecimal("25.500000"),
                "Perfume production",
                occurredAt
            )),
            1,
            5,
            6,
            2
        );
        when(getMovementHistory.execute(query)).thenReturn(result);

        mockMvc.perform(get("/api/v1/inventory/movements")
                .param("inventoryItemId", itemId.toString())
                .param("batchId", batchId.toString())
                .param("type", "CONSUMPTION")
                .param("from", from.toString())
                .param("to", to.toString())
                .param("page", "1")
                .param("size", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.size").value(5))
            .andExpect(jsonPath("$.totalElements").value(6))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.content[0].movementId").value(movementId.toString()))
            .andExpect(jsonPath("$.content[0].inventoryItemId").value(itemId.toString()))
            .andExpect(jsonPath("$.content[0].inventoryItemName").value("Good Girl Essence"))
            .andExpect(jsonPath("$.content[0].unitOfMeasure").value("MILLILITER"))
            .andExpect(jsonPath("$.content[0].inventoryItemActive").value(true))
            .andExpect(jsonPath("$.content[0].batchId").value(batchId.toString()))
            .andExpect(jsonPath("$.content[0].lotCode").value("GG-01"))
            .andExpect(jsonPath("$.content[0].type").value("CONSUMPTION"))
            .andExpect(jsonPath("$.content[0].quantity").value(25.5))
            .andExpect(jsonPath("$.content[0].reason").value("Perfume production"))
            .andExpect(jsonPath("$.content[0].occurredAt").value(occurredAt.toString()));

        verify(getMovementHistory).execute(query);
    }

    @Test
    void shouldUseDefaultPagination() throws Exception {
        var query = new GetMovementHistoryQuery(null, null, null, null, null, 0, 20);
        when(getMovementHistory.execute(query))
            .thenReturn(new MovementHistoryResult(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/inventory/movements"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isEmpty())
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20));

        verify(getMovementHistory).execute(query);
    }

    @Test
    void shouldReturnStandardValidationErrorForInvalidPagination() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/movements")
                .param("page", "-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_MOVEMENT_HISTORY_QUERY"))
            .andExpect(jsonPath("$.details.page").value("must be zero or positive"));
    }
}
