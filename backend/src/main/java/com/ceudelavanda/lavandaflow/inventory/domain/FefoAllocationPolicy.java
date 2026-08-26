package com.ceudelavanda.lavandaflow.inventory.domain;

import com.ceudelavanda.lavandaflow.inventory.domain.exception.InsufficientEligibleStockException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Pure policy that plans an all-or-nothing First Expired, First Out (FEFO)
 * withdrawal.
 *
 * <p>A batch is eligible only when it has positive balance and an expiration
 * date strictly after {@code today}; a batch expiring on {@code today} is
 * already expired. Eligible batches are ordered by expiration date, then
 * smaller remaining balance, then received date, and finally UUID. The policy
 * performs no persistence or mutation, so insufficient eligible stock rejects
 * the entire request before any balance changes.</p>
 */
public final class FefoAllocationPolicy {

    private static final Comparator<Batch> FEFO_ORDER = Comparator
        .comparing(Batch::getExpiresAt)
        .thenComparing(Batch::getCurrentQuantity)
        .thenComparing(Batch::getReceivedAt)
        .thenComparing(Batch::getId);

    /**
     * Calculates a complete FEFO allocation plan for the requested quantity.
     *
     * @throws IllegalArgumentException if required input is missing or the requested quantity is not positive
     * @throws InsufficientEligibleStockException if eligible batch balance is insufficient
     */
    public FefoAllocationPlan allocate(
        UUID inventoryItemId,
        List<Batch> candidateBatches,
        BigDecimal requestedQuantity,
        LocalDate today
    ) {
        requireNonNull(inventoryItemId, "inventoryItemId");
        requireNonNull(candidateBatches, "candidateBatches");
        requireNonNull(today, "today");
        requirePositive(requestedQuantity, "requestedQuantity");

        var eligibleBatches = candidateBatches.stream()
            .filter(batch -> isEligible(batch, today))
            .sorted(FEFO_ORDER)
            .toList();
        var availableQuantity = eligibleBatches.stream()
            .map(Batch::getCurrentQuantity)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (availableQuantity.compareTo(requestedQuantity) < 0) {
            throw new InsufficientEligibleStockException(
                inventoryItemId,
                requestedQuantity,
                availableQuantity
            );
        }

        var remainingQuantity = requestedQuantity;
        var allocations = new java.util.ArrayList<BatchAllocation>();
        for (var batch : eligibleBatches) {
            if (remainingQuantity.signum() == 0) {
                break;
            }

            var quantity = batch.getCurrentQuantity().min(remainingQuantity);
            allocations.add(new BatchAllocation(batch.getId(), quantity));
            remainingQuantity = remainingQuantity.subtract(quantity);
        }

        return new FefoAllocationPlan(
            inventoryItemId,
            requestedQuantity,
            requestedQuantity,
            allocations
        );
    }

    private static boolean isEligible(Batch batch, LocalDate today) {
        return batch != null
            && batch.getCurrentQuantity().signum() > 0
            && batch.getExpiresAt() != null
            && batch.getExpiresAt().isAfter(today);
    }

    private static void requirePositive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(field + " must be greater than zero");
        }
    }

    private static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        return value;
    }
}
