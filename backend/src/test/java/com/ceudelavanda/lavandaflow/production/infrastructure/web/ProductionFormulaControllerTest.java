package com.ceudelavanda.lavandaflow.production.infrastructure.web;

import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.production.application.formula.CreateProductionFormula;
import com.ceudelavanda.lavandaflow.production.application.formula.GetProductionFormula;
import com.ceudelavanda.lavandaflow.production.application.formula.GetProductionFormulaRequirements;
import com.ceudelavanda.lavandaflow.production.application.formula.ListProductionFormulas;
import com.ceudelavanda.lavandaflow.production.application.formula.ProductionFormulaDefinitionCommand;
import com.ceudelavanda.lavandaflow.production.application.formula.ProductionFormulaIngredientCommand;
import com.ceudelavanda.lavandaflow.production.application.formula.ProductionFormulaNotFoundException;
import com.ceudelavanda.lavandaflow.production.application.formula.ProductionFormulaRequirementsResult;
import com.ceudelavanda.lavandaflow.production.application.formula.ProductionFormulaResult;
import com.ceudelavanda.lavandaflow.production.application.formula.UpdateProductionFormula;
import com.ceudelavanda.lavandaflow.production.domain.ProductionFormulaKind;
import com.ceudelavanda.lavandaflow.shared.config.ClockConfig;
import com.ceudelavanda.lavandaflow.shared.config.ExactDecimalJsonConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductionFormulaController.class)
@Import({ClockConfig.class, ExactDecimalJsonConfiguration.class})
@WithMockUser
class ProductionFormulaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private CreateProductionFormula createProductionFormula;
    @MockitoBean private UpdateProductionFormula updateProductionFormula;
    @MockitoBean private GetProductionFormula getProductionFormula;
    @MockitoBean private ListProductionFormulas listProductionFormulas;
    @MockitoBean private GetProductionFormulaRequirements getProductionFormulaRequirements;

    @Test
    void shouldCreateProductionFormulaWithBackwardCompatibleStandardKind() throws Exception {
        var formulaId = UUID.randomUUID();
        var outputItemId = UUID.randomUUID();
        var ingredientItemId = UUID.randomUUID();
        var command = command(outputItemId, ingredientItemId, "1000", "250", ProductionFormulaKind.STANDARD);
        var result = result(formulaId, outputItemId, ingredientItemId, "1000", "250", ProductionFormulaKind.STANDARD);
        when(createProductionFormula.execute(command)).thenReturn(result);

        mockMvc.perform(post("/api/v1/production/formulas")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(outputItemId, ingredientItemId, "1000", "250", null)))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/v1/production/formulas/" + formulaId))
            .andExpect(jsonPath("$.kind").value("STANDARD"));

        verify(createProductionFormula).execute(command);
    }

    @Test
    void shouldExposePackagedFormulaKindAndScaledRequirements() throws Exception {
        var formulaId = UUID.randomUUID();
        var outputItemId = UUID.randomUUID();
        var bulkItemId = UUID.randomUUID();
        var command = command(outputItemId, bulkItemId, "1", "30", ProductionFormulaKind.PACKAGED_FILLING);
        var result = result(formulaId, outputItemId, bulkItemId, "1", "30", ProductionFormulaKind.PACKAGED_FILLING);
        when(createProductionFormula.execute(command)).thenReturn(result);
        when(getProductionFormulaRequirements.execute(formulaId, new BigDecimal("5"))).thenReturn(
            new ProductionFormulaRequirementsResult(
                formulaId,
                outputItemId,
                new BigDecimal("5"),
                UnitOfMeasure.UNIT,
                List.of(new ProductionFormulaRequirementsResult.RequirementResult(
                    bulkItemId,
                    new BigDecimal("150"),
                    UnitOfMeasure.MILLILITER
                ))
            )
        );

        mockMvc.perform(post("/api/v1/production/formulas")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(outputItemId, bulkItemId, "1", "30", "PACKAGED_FILLING")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.kind").value("PACKAGED_FILLING"));

        mockMvc.perform(get("/api/v1/production/formulas/{formulaId}/requirements", formulaId)
                .queryParam("outputQuantity", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.formulaId").value(formulaId.toString()))
            .andExpect(jsonPath("$.outputQuantity").value("5"))
            .andExpect(jsonPath("$.outputUnitOfMeasure").value("UNIT"))
            .andExpect(jsonPath("$.requirements[0].inventoryItemId").value(bulkItemId.toString()))
            .andExpect(jsonPath("$.requirements[0].quantity").value("150"))
            .andExpect(jsonPath("$.requirements[0].unitOfMeasure").value("MILLILITER"));
    }

    @Test
    void shouldExposeUpdateGetAndListContracts() throws Exception {
        var formulaId = UUID.randomUUID();
        var outputItemId = UUID.randomUUID();
        var ingredientItemId = UUID.randomUUID();
        var command = command(outputItemId, ingredientItemId, "500", "50", ProductionFormulaKind.STANDARD);
        var result = result(formulaId, outputItemId, ingredientItemId, "500", "50", ProductionFormulaKind.STANDARD);
        when(updateProductionFormula.execute(formulaId, command)).thenReturn(result);
        when(getProductionFormula.execute(formulaId)).thenReturn(result);
        when(listProductionFormulas.execute()).thenReturn(List.of(result));

        mockMvc.perform(put("/api/v1/production/formulas/{formulaId}", formulaId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(outputItemId, ingredientItemId, "500", "50", null)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(formulaId.toString()));

        mockMvc.perform(get("/api/v1/production/formulas/{formulaId}", formulaId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.outputQuantity").value("500"))
            .andExpect(jsonPath("$.ingredients[0].quantity").value("50"));

        mockMvc.perform(get("/api/v1/production/formulas"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(formulaId.toString()));
    }

    @Test
    void shouldRejectInvalidRequestAtHttpBoundary() throws Exception {
        mockMvc.perform(post("/api/v1/production/formulas")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "outputInventoryItemId": null,
                      "outputQuantity": 0,
                      "ingredients": []
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.details.outputInventoryItemId").exists())
            .andExpect(jsonPath("$.details.outputQuantity").exists())
            .andExpect(jsonPath("$.details.ingredients").exists());
    }

    @Test
    void shouldReturnStableNotFoundContract() throws Exception {
        var formulaId = UUID.randomUUID();
        when(getProductionFormula.execute(formulaId)).thenThrow(new ProductionFormulaNotFoundException(formulaId));

        mockMvc.perform(get("/api/v1/production/formulas/{formulaId}", formulaId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("PRODUCTION_FORMULA_NOT_FOUND"))
            .andExpect(jsonPath("$.details.formulaId").value(formulaId.toString()));
    }

    private static ProductionFormulaDefinitionCommand command(
        UUID outputItemId,
        UUID ingredientItemId,
        String outputQuantity,
        String ingredientQuantity,
        ProductionFormulaKind kind
    ) {
        return new ProductionFormulaDefinitionCommand(
            outputItemId,
            new BigDecimal(outputQuantity),
            List.of(new ProductionFormulaIngredientCommand(
                ingredientItemId, new BigDecimal(ingredientQuantity)
            )),
            kind
        );
    }

    private static ProductionFormulaResult result(
        UUID formulaId,
        UUID outputItemId,
        UUID ingredientItemId,
        String outputQuantity,
        String ingredientQuantity,
        ProductionFormulaKind kind
    ) {
        return new ProductionFormulaResult(
            formulaId,
            outputItemId,
            new BigDecimal(outputQuantity),
            kind == ProductionFormulaKind.PACKAGED_FILLING ? UnitOfMeasure.UNIT : UnitOfMeasure.MILLILITER,
            List.of(new ProductionFormulaResult.IngredientResult(
                ingredientItemId,
                new BigDecimal(ingredientQuantity),
                UnitOfMeasure.MILLILITER
            )),
            kind
        );
    }

    private static String json(
        UUID outputItemId,
        UUID ingredientItemId,
        String outputQuantity,
        String ingredientQuantity,
        String kind
    ) {
        var kindField = kind == null ? "" : ",\n  \"kind\": \"" + kind + "\"";
        return """
            {
              "outputInventoryItemId": "%s",
              "outputQuantity": %s,
              "ingredients": [
                {
                  "inventoryItemId": "%s",
                  "quantity": %s
                }
              ]%s
            }
            """.formatted(outputItemId, outputQuantity, ingredientItemId, ingredientQuantity, kindField);
    }
}
