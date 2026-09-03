package com.ceudelavanda.lavandaflow.production.application.execution;

import com.ceudelavanda.lavandaflow.catalog.ProductionItemReference;
import com.ceudelavanda.lavandaflow.catalog.ProductionItemReferenceLookup;
import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.inventory.ProductionBatchReference;
import com.ceudelavanda.lavandaflow.inventory.ProductionBatchReferenceLookup;
import com.ceudelavanda.lavandaflow.inventory.ProductionOutputBatch;
import com.ceudelavanda.lavandaflow.inventory.ProductionSourceAllocation;
import com.ceudelavanda.lavandaflow.inventory.ProductionStockApplication;
import com.ceudelavanda.lavandaflow.inventory.ProductionStockCommand;
import com.ceudelavanda.lavandaflow.production.application.formula.ProductionFormulaNotFoundException;
import com.ceudelavanda.lavandaflow.production.application.lot.AllocateInternalProductionLotCode;
import com.ceudelavanda.lavandaflow.production.application.lot.AllocateInternalProductionLotCodeCommand;
import com.ceudelavanda.lavandaflow.production.domain.FormulaIngredient;
import com.ceudelavanda.lavandaflow.production.domain.ProductionConsumption;
import com.ceudelavanda.lavandaflow.production.domain.ProductionExecution;
import com.ceudelavanda.lavandaflow.production.domain.ProductionExecutionRepository;
import com.ceudelavanda.lavandaflow.production.domain.ProductionFormula;
import com.ceudelavanda.lavandaflow.production.domain.ProductionFormulaRepository;
import com.ceudelavanda.lavandaflow.production.domain.ProductionLotCodeMode;
import com.ceudelavanda.lavandaflow.production.domain.exception.InvalidProductionExecutionException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the single local transaction that registers one completed production operation.
 *
 * <p>Formula validation, definitive generated lot allocation, inventory effects, exact
 * consumption history, and output genealogy references either commit together or roll back.</p>
 */
@Service
@RequiredArgsConstructor
public class RegisterProduction {

    private static final int MAX_MANUAL_LOT_CODE_LENGTH = 255;

    private final ProductionFormulaRepository productionFormulaRepository;
    private final ProductionExecutionRepository productionExecutionRepository;
    private final ProductionItemReferenceLookup productionItemReferenceLookup;
    private final ProductionBatchReferenceLookup productionBatchReferenceLookup;
    private final AllocateInternalProductionLotCode allocateInternalProductionLotCode;
    private final ProductionStockApplication productionStockApplication;
    private final Clock clock;

    @Transactional
    public RegisterProductionResult execute(RegisterProductionCommand command) {
        var validated = validateCommand(command);
        var formula = productionFormulaRepository.findById(validated.formulaId())
            .orElseThrow(() -> new ProductionFormulaNotFoundException(validated.formulaId()));

        validateFormulaCatalogState(formula);
        var sourceReferences = validateFormulaAllocations(
            formula,
            validated.outputQuantity(),
            validated.sourceAllocations()
        );
        var lotCode = resolveLotCode(validated, formula.getOutputInventoryItemId());

        var inventoryResult = productionStockApplication.apply(new ProductionStockCommand(
            validated.sourceAllocations().stream()
                .map(allocation -> new ProductionSourceAllocation(allocation.batchId(), allocation.quantity()))
                .toList(),
            new ProductionOutputBatch(
                formula.getOutputInventoryItemId(),
                lotCode,
                validated.outputQuantity(),
                validated.outputReceivedAt(),
                validated.outputExpiresAt()
            )
        ));

        var consumptions = inventoryResult.sourceConsumptions().stream()
            .map(consumption -> {
                var sourceReference = sourceReferences.get(consumption.batchId());
                if (sourceReference == null) {
                    throw new InvalidProductionExecutionException(
                        "consumptions",
                        "Inventory returned a source consumption that was not part of the validated production allocation"
                    );
                }
                return new ProductionConsumption(
                    consumption.batchId(),
                    sourceReference.inventoryItemId(),
                    consumption.movementId(),
                    consumption.quantity()
                );
            })
            .toList();

        var execution = ProductionExecution.create(
            formula.getId(),
            formula.getOutputInventoryItemId(),
            inventoryResult.outputBatchId(),
            validated.outputQuantity(),
            lotCode,
            validated.lotCodeMode(),
            validated.productionDate(),
            validated.outputReceivedAt(),
            validated.outputExpiresAt(),
            Instant.now(clock),
            consumptions
        );

        return RegisterProductionResult.from(productionExecutionRepository.save(execution));
    }

    private RegisterProductionCommand validateCommand(RegisterProductionCommand command) {
        if (command == null) {
            throw new InvalidProductionExecutionException("command", "Production registration command must not be null");
        }
        if (command.formulaId() == null) {
            throw new InvalidProductionExecutionException("formulaId", "Formula id must not be null");
        }
        requireSupportedPositiveQuantity(command.outputQuantity(), "outputQuantity");
        if (command.sourceAllocations() == null || command.sourceAllocations().isEmpty()) {
            throw new InvalidProductionExecutionException("sourceAllocations", "At least one exact source allocation is required");
        }
        if (command.productionDate() == null) {
            throw new InvalidProductionExecutionException("productionDate", "Production date must not be null");
        }
        if (command.outputReceivedAt() == null) {
            throw new InvalidProductionExecutionException("outputReceivedAt", "Output received date must not be null");
        }
        if (command.outputExpiresAt() != null && command.outputExpiresAt().isBefore(command.outputReceivedAt())) {
            throw new InvalidProductionExecutionException(
                "outputExpiresAt",
                "Output expiration date must not be before output received date"
            );
        }
        if (command.lotCodeMode() == null) {
            throw new InvalidProductionExecutionException("lotCodeMode", "Lot code mode must not be null");
        }

        var seenBatchIds = new HashSet<UUID>();
        for (var allocation : command.sourceAllocations()) {
            if (allocation == null || allocation.batchId() == null) {
                throw new InvalidProductionExecutionException(
                    "sourceAllocations",
                    "Source allocations and batch ids must not be null"
                );
            }
            requireSupportedPositiveQuantity(allocation.quantity(), "sourceAllocation.quantity");
            if (!seenBatchIds.add(allocation.batchId())) {
                throw new InvalidProductionExecutionException(
                    "sourceAllocations",
                    "Source allocations must not contain duplicate batch ids"
                );
            }
        }

        validateLotInput(command.lotCodeMode(), command.manualLotCode());
        return command;
    }

    private void validateFormulaCatalogState(ProductionFormula formula) {
        requireActiveCatalogItem(
            formula.getOutputInventoryItemId(),
            formula.getOutputUnitOfMeasure()
        );
        for (var ingredient : formula.getIngredients()) {
            requireActiveCatalogItem(ingredient.inventoryItemId(), ingredient.unitOfMeasure());
        }
    }

    private ProductionItemReference requireActiveCatalogItem(
        UUID inventoryItemId,
        UnitOfMeasure formulaUnit
    ) {
        var item = productionItemReferenceLookup.findByInventoryItemId(inventoryItemId)
            .orElseThrow(() -> new ProductionRegistrationCatalogItemNotFoundException(inventoryItemId));
        if (!item.active()) {
            throw new InactiveProductionRegistrationCatalogItemException(inventoryItemId);
        }
        if (item.unitOfMeasure() != formulaUnit) {
            throw new ProductionFormulaUnitMismatchException(
                inventoryItemId,
                formulaUnit,
                item.unitOfMeasure()
            );
        }
        return item;
    }

    private Map<UUID, ProductionBatchReference> validateFormulaAllocations(
        ProductionFormula formula,
        BigDecimal requestedOutputQuantity,
        List<ProductionSourceAllocationCommand> sourceAllocations
    ) {
        var sourceReferences = new LinkedHashMap<UUID, ProductionBatchReference>();
        var actualTotals = new HashMap<UUID, BigDecimal>();
        for (var allocation : sourceAllocations) {
            var reference = productionBatchReferenceLookup.findByBatchId(allocation.batchId())
                .orElseThrow(() -> new ProductionSourceBatchNotFoundException(allocation.batchId()));
            sourceReferences.put(allocation.batchId(), reference);
            actualTotals.merge(reference.inventoryItemId(), allocation.quantity(), BigDecimal::add);
        }

        var requiredTotals = new HashMap<UUID, BigDecimal>();
        for (var ingredient : formula.getIngredients()) {
            requiredTotals.put(
                ingredient.inventoryItemId(),
                scaleRequirement(ingredient, requestedOutputQuantity, formula.getOutputQuantity())
            );
        }

        for (var actual : actualTotals.entrySet()) {
            if (!requiredTotals.containsKey(actual.getKey())) {
                throw new InvalidProductionAllocationException(
                    actual.getKey(),
                    BigDecimal.ZERO.toPlainString(),
                    actual.getValue().toPlainString()
                );
            }
        }
        for (var required : requiredTotals.entrySet()) {
            var actual = actualTotals.getOrDefault(required.getKey(), BigDecimal.ZERO);
            if (actual.compareTo(required.getValue()) != 0) {
                throw new InvalidProductionAllocationException(
                    required.getKey(),
                    required.getValue().toPlainString(),
                    actual.toPlainString()
                );
            }
        }
        return sourceReferences;
    }

    private BigDecimal scaleRequirement(
        FormulaIngredient ingredient,
        BigDecimal requestedOutputQuantity,
        BigDecimal referenceOutputQuantity
    ) {
        try {
            var scaled = ingredient.quantity()
                .multiply(requestedOutputQuantity)
                .divide(referenceOutputQuantity, 6, RoundingMode.UNNECESSARY);
            var integerDigits = Math.max(scaled.precision() - scaled.scale(), 0);
            if (integerDigits > 13 || scaled.signum() <= 0) {
                throw new UnrepresentableProductionRequirementException(ingredient.inventoryItemId());
            }
            return scaled;
        } catch (ArithmeticException exception) {
            throw new UnrepresentableProductionRequirementException(ingredient.inventoryItemId());
        }
    }

    private String resolveLotCode(RegisterProductionCommand command, UUID outputInventoryItemId) {
        if (command.lotCodeMode() == ProductionLotCodeMode.GENERATED) {
            return allocateInternalProductionLotCode.execute(
                new AllocateInternalProductionLotCodeCommand(outputInventoryItemId, command.productionDate())
            ).value();
        }
        return command.manualLotCode().trim();
    }

    private void validateLotInput(ProductionLotCodeMode mode, String manualLotCode) {
        if (mode == ProductionLotCodeMode.GENERATED) {
            if (manualLotCode != null && !manualLotCode.isBlank()) {
                throw new InvalidManualProductionLotCodeException(
                    "manualLotCode must be omitted when lotCodeMode is GENERATED"
                );
            }
            return;
        }
        if (manualLotCode == null || manualLotCode.isBlank()) {
            throw new InvalidManualProductionLotCodeException(
                "manualLotCode is required when lotCodeMode is MANUAL"
            );
        }
        if (manualLotCode.trim().length() > MAX_MANUAL_LOT_CODE_LENGTH) {
            throw new InvalidManualProductionLotCodeException(
                "manualLotCode must not exceed " + MAX_MANUAL_LOT_CODE_LENGTH + " characters"
            );
        }
    }

    private void requireSupportedPositiveQuantity(BigDecimal quantity, String field) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new InvalidProductionExecutionException(field, "Quantity must be greater than zero");
        }
        var fractionDigits = Math.max(quantity.scale(), 0);
        var integerDigits = Math.max(quantity.precision() - quantity.scale(), 0);
        if (integerDigits > 13 || fractionDigits > 6) {
            throw new InvalidProductionExecutionException(
                field,
                "Quantity must have at most 13 integer digits and 6 fractional digits"
            );
        }
    }
}
