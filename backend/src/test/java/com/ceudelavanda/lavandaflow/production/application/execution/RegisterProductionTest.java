package com.ceudelavanda.lavandaflow.production.application.execution;

import com.ceudelavanda.lavandaflow.catalog.ProductionItemReference;
import com.ceudelavanda.lavandaflow.catalog.ProductionItemReferenceLookup;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.inventory.ProductionBatchReference;
import com.ceudelavanda.lavandaflow.inventory.ProductionBatchReferenceLookup;
import com.ceudelavanda.lavandaflow.inventory.ProductionSourceConsumptionResult;
import com.ceudelavanda.lavandaflow.inventory.ProductionStockApplication;
import com.ceudelavanda.lavandaflow.inventory.ProductionStockCommand;
import com.ceudelavanda.lavandaflow.inventory.ProductionStockResult;
import com.ceudelavanda.lavandaflow.production.application.lot.AllocateInternalProductionLotCode;
import com.ceudelavanda.lavandaflow.production.application.lot.AllocateInternalProductionLotCodeCommand;
import com.ceudelavanda.lavandaflow.production.application.lot.InternalProductionLotCode;
import com.ceudelavanda.lavandaflow.production.domain.FormulaIngredient;
import com.ceudelavanda.lavandaflow.production.domain.ProductionExecution;
import com.ceudelavanda.lavandaflow.production.domain.ProductionExecutionRepository;
import com.ceudelavanda.lavandaflow.production.domain.ProductionFormula;
import com.ceudelavanda.lavandaflow.production.domain.ProductionFormulaRepository;
import com.ceudelavanda.lavandaflow.production.domain.ProductionLotCodeMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterProductionTest {

    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2026-09-03T12:00:00Z"),
        ZoneId.of("America/Sao_Paulo")
    );

    @Mock private ProductionFormulaRepository productionFormulaRepository;
    @Mock private ProductionExecutionRepository productionExecutionRepository;
    @Mock private ProductionItemReferenceLookup productionItemReferenceLookup;
    @Mock private ProductionBatchReferenceLookup productionBatchReferenceLookup;
    @Mock private AllocateInternalProductionLotCode allocateInternalProductionLotCode;
    @Mock private ProductionStockApplication productionStockApplication;

    private RegisterProduction registerProduction;

    @BeforeEach
    void setUp() {
        registerProduction = new RegisterProduction(
            productionFormulaRepository,
            productionExecutionRepository,
            productionItemReferenceLookup,
            productionBatchReferenceLookup,
            allocateInternalProductionLotCode,
            productionStockApplication,
            CLOCK
        );
        when(productionExecutionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shouldScaleFormulaAcrossMultipleExactBatchesAndUseGeneratedLot() {
        var formulaId = UUID.randomUUID();
        var outputItemId = UUID.randomUUID();
        var ingredientItemId = UUID.randomUUID();
        var firstBatchId = UUID.randomUUID();
        var secondBatchId = UUID.randomUUID();
        var formula = formula(formulaId, outputItemId, ingredientItemId, "10", "5");
        prepareFormula(formula, outputItemId, ingredientItemId);
        when(productionBatchReferenceLookup.findByBatchId(firstBatchId))
            .thenReturn(Optional.of(new ProductionBatchReference(firstBatchId, ingredientItemId)));
        when(productionBatchReferenceLookup.findByBatchId(secondBatchId))
            .thenReturn(Optional.of(new ProductionBatchReference(secondBatchId, ingredientItemId)));
        when(allocateInternalProductionLotCode.execute(any()))
            .thenReturn(new InternalProductionLotCode("BDS-000-001-09-2026"));

        var outputBatchId = UUID.randomUUID();
        when(productionStockApplication.apply(any())).thenReturn(new ProductionStockResult(
            outputBatchId,
            UUID.randomUUID(),
            List.of(
                new ProductionSourceConsumptionResult(firstBatchId, UUID.randomUUID(), new BigDecimal("4")),
                new ProductionSourceConsumptionResult(secondBatchId, UUID.randomUUID(), new BigDecimal("6"))
            )
        ));

        var result = registerProduction.execute(command(
            formulaId,
            "20",
            List.of(
                new ProductionSourceAllocationCommand(firstBatchId, new BigDecimal("4")),
                new ProductionSourceAllocationCommand(secondBatchId, new BigDecimal("6"))
            ),
            ProductionLotCodeMode.GENERATED,
            null
        ));

        assertThat(result.outputBatchId()).isEqualTo(outputBatchId);
        assertThat(result.lotCode()).isEqualTo("BDS-000-001-09-2026");
        assertThat(result.consumptions()).hasSize(2);
        verify(allocateInternalProductionLotCode).execute(
            new AllocateInternalProductionLotCodeCommand(outputItemId, LocalDate.of(2026, 9, 3))
        );

        var stockCommand = ArgumentCaptor.forClass(ProductionStockCommand.class);
        verify(productionStockApplication).apply(stockCommand.capture());
        assertThat(stockCommand.getValue().outputBatch().inventoryItemId()).isEqualTo(outputItemId);
        assertThat(stockCommand.getValue().outputBatch().lotCode()).isEqualTo("BDS-000-001-09-2026");
        assertThat(stockCommand.getValue().outputBatch().quantity()).isEqualByComparingTo("20");
    }

    @Test
    void shouldUseManualLotWithoutAllocatingGeneratedSequence() {
        var formulaId = UUID.randomUUID();
        var outputItemId = UUID.randomUUID();
        var ingredientItemId = UUID.randomUUID();
        var batchId = UUID.randomUUID();
        var formula = formula(formulaId, outputItemId, ingredientItemId, "5", "5");
        prepareFormula(formula, outputItemId, ingredientItemId);
        when(productionBatchReferenceLookup.findByBatchId(batchId))
            .thenReturn(Optional.of(new ProductionBatchReference(batchId, ingredientItemId)));
        when(productionStockApplication.apply(any())).thenReturn(new ProductionStockResult(
            UUID.randomUUID(),
            UUID.randomUUID(),
            List.of(new ProductionSourceConsumptionResult(batchId, UUID.randomUUID(), new BigDecimal("5")))
        ));

        var result = registerProduction.execute(command(
            formulaId,
            "5",
            List.of(new ProductionSourceAllocationCommand(batchId, new BigDecimal("5"))),
            ProductionLotCodeMode.MANUAL,
            "  MANUAL-LOT-42  "
        ));

        assertThat(result.lotCode()).isEqualTo("MANUAL-LOT-42");
        assertThat(result.lotCodeMode()).isEqualTo(ProductionLotCodeMode.MANUAL);
        verifyNoInteractions(allocateInternalProductionLotCode);
    }

    @Test
    void shouldRejectAllocationTotalsThatDoNotMatchScaledFormula() {
        var formulaId = UUID.randomUUID();
        var outputItemId = UUID.randomUUID();
        var ingredientItemId = UUID.randomUUID();
        var batchId = UUID.randomUUID();
        var formula = formula(formulaId, outputItemId, ingredientItemId, "10", "5");
        prepareFormula(formula, outputItemId, ingredientItemId);
        when(productionBatchReferenceLookup.findByBatchId(batchId))
            .thenReturn(Optional.of(new ProductionBatchReference(batchId, ingredientItemId)));

        assertThatThrownBy(() -> registerProduction.execute(command(
            formulaId,
            "20",
            List.of(new ProductionSourceAllocationCommand(batchId, new BigDecimal("9"))),
            ProductionLotCodeMode.GENERATED,
            null
        ))).isInstanceOf(InvalidProductionAllocationException.class);

        verifyNoInteractions(allocateInternalProductionLotCode, productionStockApplication);
        verify(productionExecutionRepository, never()).save(any());
    }

    @Test
    void shouldRejectSourceItemThatIsNotPartOfFormula() {
        var formulaId = UUID.randomUUID();
        var outputItemId = UUID.randomUUID();
        var ingredientItemId = UUID.randomUUID();
        var extraItemId = UUID.randomUUID();
        var batchId = UUID.randomUUID();
        var formula = formula(formulaId, outputItemId, ingredientItemId, "5", "5");
        prepareFormula(formula, outputItemId, ingredientItemId);
        when(productionBatchReferenceLookup.findByBatchId(batchId))
            .thenReturn(Optional.of(new ProductionBatchReference(batchId, extraItemId)));

        assertThatThrownBy(() -> registerProduction.execute(command(
            formulaId,
            "5",
            List.of(new ProductionSourceAllocationCommand(batchId, new BigDecimal("5"))),
            ProductionLotCodeMode.GENERATED,
            null
        ))).isInstanceOf(InvalidProductionAllocationException.class);
    }

    @Test
    void shouldRejectScaledRequirementThatNeedsRounding() {
        var formulaId = UUID.randomUUID();
        var outputItemId = UUID.randomUUID();
        var ingredientItemId = UUID.randomUUID();
        var batchId = UUID.randomUUID();
        var formula = formula(formulaId, outputItemId, ingredientItemId, "3", "1");
        prepareFormula(formula, outputItemId, ingredientItemId);
        when(productionBatchReferenceLookup.findByBatchId(batchId))
            .thenReturn(Optional.of(new ProductionBatchReference(batchId, ingredientItemId)));

        assertThatThrownBy(() -> registerProduction.execute(command(
            formulaId,
            "1",
            List.of(new ProductionSourceAllocationCommand(batchId, new BigDecimal("0.333333"))),
            ProductionLotCodeMode.GENERATED,
            null
        ))).isInstanceOf(UnrepresentableProductionRequirementException.class);
    }

    @Test
    void shouldRejectInactiveOrUnitChangedFormulaCatalogState() {
        var formulaId = UUID.randomUUID();
        var outputItemId = UUID.randomUUID();
        var ingredientItemId = UUID.randomUUID();
        var formula = formula(formulaId, outputItemId, ingredientItemId, "5", "5");
        when(productionFormulaRepository.findById(formulaId)).thenReturn(Optional.of(formula));
        when(productionItemReferenceLookup.findByInventoryItemId(outputItemId))
            .thenReturn(Optional.of(reference(outputItemId, UnitOfMeasure.MILLILITER, false)));

        assertThatThrownBy(() -> registerProduction.execute(command(
            formulaId,
            "5",
            List.of(new ProductionSourceAllocationCommand(UUID.randomUUID(), new BigDecimal("5"))),
            ProductionLotCodeMode.GENERATED,
            null
        ))).isInstanceOf(InactiveProductionRegistrationCatalogItemException.class);

        when(productionItemReferenceLookup.findByInventoryItemId(outputItemId))
            .thenReturn(Optional.of(reference(outputItemId, UnitOfMeasure.UNIT, true)));
        assertThatThrownBy(() -> registerProduction.execute(command(
            formulaId,
            "5",
            List.of(new ProductionSourceAllocationCommand(UUID.randomUUID(), new BigDecimal("5"))),
            ProductionLotCodeMode.GENERATED,
            null
        ))).isInstanceOf(ProductionFormulaUnitMismatchException.class);
    }

    @Test
    void shouldRejectAmbiguousLotInputsBeforeSequenceOrInventoryEffects() {
        var formulaId = UUID.randomUUID();
        assertThatThrownBy(() -> registerProduction.execute(command(
            formulaId,
            "1",
            List.of(new ProductionSourceAllocationCommand(UUID.randomUUID(), BigDecimal.ONE)),
            ProductionLotCodeMode.GENERATED,
            "MANUAL"
        ))).isInstanceOf(InvalidManualProductionLotCodeException.class);

        assertThatThrownBy(() -> registerProduction.execute(command(
            formulaId,
            "1",
            List.of(new ProductionSourceAllocationCommand(UUID.randomUUID(), BigDecimal.ONE)),
            ProductionLotCodeMode.MANUAL,
            "   "
        ))).isInstanceOf(InvalidManualProductionLotCodeException.class);

        verifyNoInteractions(productionFormulaRepository, allocateInternalProductionLotCode, productionStockApplication);
    }

    private void prepareFormula(ProductionFormula formula, UUID outputItemId, UUID ingredientItemId) {
        when(productionFormulaRepository.findById(formula.getId())).thenReturn(Optional.of(formula));
        when(productionItemReferenceLookup.findByInventoryItemId(outputItemId))
            .thenReturn(Optional.of(reference(outputItemId, UnitOfMeasure.MILLILITER, true)));
        when(productionItemReferenceLookup.findByInventoryItemId(ingredientItemId))
            .thenReturn(Optional.of(reference(ingredientItemId, UnitOfMeasure.MILLILITER, true)));
    }

    private static ProductionItemReference reference(UUID itemId, UnitOfMeasure unit, boolean active) {
        return new ProductionItemReference(itemId, unit, active, null, "BDS");
    }

    private static ProductionFormula formula(
        UUID formulaId,
        UUID outputItemId,
        UUID ingredientItemId,
        String outputQuantity,
        String ingredientQuantity
    ) {
        return new ProductionFormula(
            formulaId,
            outputItemId,
            new BigDecimal(outputQuantity),
            UnitOfMeasure.MILLILITER,
            List.of(new FormulaIngredient(
                ingredientItemId,
                new BigDecimal(ingredientQuantity),
                UnitOfMeasure.MILLILITER
            ))
        );
    }

    private static RegisterProductionCommand command(
        UUID formulaId,
        String outputQuantity,
        List<ProductionSourceAllocationCommand> allocations,
        ProductionLotCodeMode mode,
        String manualLotCode
    ) {
        return new RegisterProductionCommand(
            formulaId,
            new BigDecimal(outputQuantity),
            allocations,
            LocalDate.of(2026, 9, 3),
            LocalDate.of(2026, 9, 3),
            null,
            mode,
            manualLotCode
        );
    }
}
