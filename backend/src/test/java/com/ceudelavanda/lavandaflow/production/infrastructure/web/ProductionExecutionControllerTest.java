package com.ceudelavanda.lavandaflow.production.infrastructure.web;

import com.ceudelavanda.lavandaflow.production.application.execution.ProductionSourceAllocationCommand;
import com.ceudelavanda.lavandaflow.production.application.execution.RegisterProduction;
import com.ceudelavanda.lavandaflow.production.application.execution.RegisterProductionCommand;
import com.ceudelavanda.lavandaflow.production.application.execution.RegisterProductionResult;
import com.ceudelavanda.lavandaflow.production.application.formula.ProductionFormulaNotFoundException;
import com.ceudelavanda.lavandaflow.production.domain.ProductionLotCodeMode;
import com.ceudelavanda.lavandaflow.shared.config.ClockConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductionExecutionController.class)
@Import(ClockConfig.class)
@WithMockUser
class ProductionExecutionControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean
    private RegisterProduction registerProduction;

    @Test
    void shouldRegisterCompletedProductionExecution() throws Exception {
        var formulaId = UUID.randomUUID();
        var sourceBatchId = UUID.randomUUID();
        var sourceItemId = UUID.randomUUID();
        var sourceMovementId = UUID.randomUUID();
        var outputItemId = UUID.randomUUID();
        var outputBatchId = UUID.randomUUID();
        var executionId = UUID.randomUUID();
        var command = new RegisterProductionCommand(
            formulaId,
            new BigDecimal("10"),
            List.of(new ProductionSourceAllocationCommand(sourceBatchId, new BigDecimal("5"))),
            LocalDate.of(2026, 9, 3),
            LocalDate.of(2026, 9, 3),
            LocalDate.of(2027, 9, 3),
            ProductionLotCodeMode.GENERATED,
            null
        );
        when(registerProduction.execute(command)).thenReturn(new RegisterProductionResult(
            executionId,
            formulaId,
            outputItemId,
            outputBatchId,
            new BigDecimal("10"),
            "BDS-000-001-09-2026",
            ProductionLotCodeMode.GENERATED,
            LocalDate.of(2026, 9, 3),
            LocalDate.of(2026, 9, 3),
            LocalDate.of(2027, 9, 3),
            Instant.parse("2026-09-03T12:00:00Z"),
            List.of(new RegisterProductionResult.ConsumptionResult(
                sourceBatchId,
                sourceItemId,
                sourceMovementId,
                new BigDecimal("5")
            ))
        ));

        mockMvc.perform(post("/api/v1/production/executions")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "formulaId": "%s",
                      "outputQuantity": 10,
                      "sourceAllocations": [
                        {"batchId": "%s", "quantity": 5}
                      ],
                      "productionDate": "2026-09-03",
                      "outputReceivedAt": "2026-09-03",
                      "outputExpiresAt": "2027-09-03",
                      "lotCodeMode": "GENERATED"
                    }
                    """.formatted(formulaId, sourceBatchId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.executionId").value(executionId.toString()))
            .andExpect(jsonPath("$.outputBatchId").value(outputBatchId.toString()))
            .andExpect(jsonPath("$.lotCode").value("BDS-000-001-09-2026"))
            .andExpect(jsonPath("$.lotCodeMode").value("GENERATED"))
            .andExpect(jsonPath("$.consumptions[0].sourceBatchId").value(sourceBatchId.toString()))
            .andExpect(jsonPath("$.consumptions[0].movementId").value(sourceMovementId.toString()));

        verify(registerProduction).execute(command);
    }

    @Test
    void shouldRejectInvalidHttpRequest() throws Exception {
        mockMvc.perform(post("/api/v1/production/executions")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "formulaId": null,
                      "outputQuantity": 0,
                      "sourceAllocations": [],
                      "productionDate": null,
                      "outputReceivedAt": null,
                      "lotCodeMode": null
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.details.formulaId").exists())
            .andExpect(jsonPath("$.details.outputQuantity").exists())
            .andExpect(jsonPath("$.details.sourceAllocations").exists());
    }

    @Test
    void shouldExposeStableFormulaNotFoundError() throws Exception {
        var formulaId = UUID.randomUUID();
        var sourceBatchId = UUID.randomUUID();
        when(registerProduction.execute(org.mockito.ArgumentMatchers.any()))
            .thenThrow(new ProductionFormulaNotFoundException(formulaId));

        mockMvc.perform(post("/api/v1/production/executions")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "formulaId": "%s",
                      "outputQuantity": 1,
                      "sourceAllocations": [
                        {"batchId": "%s", "quantity": 1}
                      ],
                      "productionDate": "2026-09-03",
                      "outputReceivedAt": "2026-09-03",
                      "lotCodeMode": "MANUAL",
                      "manualLotCode": "MANUAL-001"
                    }
                    """.formatted(formulaId, sourceBatchId)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("PRODUCTION_FORMULA_NOT_FOUND"))
            .andExpect(jsonPath("$.details.formulaId").value(formulaId.toString()));
    }
}
