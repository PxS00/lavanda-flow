package com.ceudelavanda.lavandaflow.production.infrastructure.web;

import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.production.application.genealogy.BatchGenealogyNotFoundException;
import com.ceudelavanda.lavandaflow.production.application.genealogy.BatchGenealogyResult;
import com.ceudelavanda.lavandaflow.production.application.genealogy.GenealogyBatch;
import com.ceudelavanda.lavandaflow.production.application.genealogy.GenealogyBatchOrigin;
import com.ceudelavanda.lavandaflow.production.application.genealogy.GenealogyDirection;
import com.ceudelavanda.lavandaflow.production.application.genealogy.GenealogyEdge;
import com.ceudelavanda.lavandaflow.production.application.genealogy.GetBatchGenealogy;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductionGenealogyController.class)
@Import(ClockConfig.class)
@WithMockUser
class ProductionGenealogyControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private GetBatchGenealogy getBatchGenealogy;

    @Test
    void shouldExposeRecursiveGenealogyWithBothAsDefaultDirection() throws Exception {
        var rootId = UUID.randomUUID();
        var sourceId = UUID.randomUUID();
        var executionId = UUID.randomUUID();
        var formulaId = UUID.randomUUID();
        var root = batch(rootId, "FINAL", GenealogyBatchOrigin.INTERNALLY_PRODUCED);
        var source = batch(sourceId, "RAW", GenealogyBatchOrigin.EXTERNAL_OR_NON_PRODUCED);
        var edge = new GenealogyEdge(
            executionId,
            formulaId,
            LocalDate.of(2026, 9, 3),
            Instant.parse("2026-09-03T12:00:00Z"),
            new BigDecimal("2.500000"),
            source,
            root,
            List.of()
        );
        when(getBatchGenealogy.execute(rootId, GenealogyDirection.BOTH))
            .thenReturn(new BatchGenealogyResult(GenealogyDirection.BOTH, root, List.of(edge), List.of()));

        mockMvc.perform(get("/api/v1/production/genealogy/batches/{batchId}", rootId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.direction").value("BOTH"))
            .andExpect(jsonPath("$.rootBatch.batchId").value(rootId.toString()))
            .andExpect(jsonPath("$.rootBatch.origin").value("INTERNALLY_PRODUCED"))
            .andExpect(jsonPath("$.upstream[0].executionId").value(executionId.toString()))
            .andExpect(jsonPath("$.upstream[0].productionDate").value("2026-09-03"))
            .andExpect(jsonPath("$.upstream[0].completedAt").value("2026-09-03T12:00:00Z"))
            .andExpect(jsonPath("$.upstream[0].consumedQuantity").value(2.5))
            .andExpect(jsonPath("$.upstream[0].sourceBatch.lotCode").value("RAW"));

        verify(getBatchGenealogy).execute(rootId, GenealogyDirection.BOTH);
    }

    @Test
    void shouldHonorExplicitTraversalDirection() throws Exception {
        var batchId = UUID.randomUUID();
        var root = batch(batchId, "ROOT", GenealogyBatchOrigin.EXTERNAL_OR_NON_PRODUCED);
        when(getBatchGenealogy.execute(batchId, GenealogyDirection.DOWNSTREAM))
            .thenReturn(new BatchGenealogyResult(GenealogyDirection.DOWNSTREAM, root, List.of(), List.of()));

        mockMvc.perform(get("/api/v1/production/genealogy/batches/{batchId}", batchId)
                .queryParam("direction", "DOWNSTREAM"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.direction").value("DOWNSTREAM"));

        verify(getBatchGenealogy).execute(batchId, GenealogyDirection.DOWNSTREAM);
    }

    @Test
    void shouldReturnStableNotFoundContractForMissingBatch() throws Exception {
        var batchId = UUID.randomUUID();
        when(getBatchGenealogy.execute(batchId, GenealogyDirection.BOTH))
            .thenThrow(new BatchGenealogyNotFoundException(batchId));

        mockMvc.perform(get("/api/v1/production/genealogy/batches/{batchId}", batchId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("BATCH_NOT_FOUND"));
    }

    private static GenealogyBatch batch(UUID batchId, String lotCode, GenealogyBatchOrigin origin) {
        return new GenealogyBatch(
            batchId,
            origin,
            UUID.randomUUID(),
            "Item",
            "OTHER",
            UnitOfMeasure.MILLILITER,
            null,
            lotCode,
            LocalDate.of(2026, 9, 1),
            null
        );
    }
}
