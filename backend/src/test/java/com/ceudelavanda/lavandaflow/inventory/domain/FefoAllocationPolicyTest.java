package com.ceudelavanda.lavandaflow.inventory.domain;

import com.ceudelavanda.lavandaflow.inventory.domain.exception.InsufficientEligibleStockException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class FefoAllocationPolicyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 25);

    private final FefoAllocationPolicy policy = new FefoAllocationPolicy();

    @Test
    void shouldAllocateEarliestExpirationFirst() {
        var itemId = UUID.randomUUID();
        var later = batch(itemId, "00000000-0000-0000-0000-000000000002", "20", "2026-09-02", "2026-08-20");
        var earlier = batch(itemId, "00000000-0000-0000-0000-000000000001", "20", "2026-09-01", "2026-08-21");

        var plan = policy.allocate(itemId, List.of(later, earlier), decimal("20"), TODAY);

        assertThat(plan.allocations()).hasSize(1);
        assertThat(plan.allocations().getFirst().batchId()).isEqualTo(earlier.getId());
        assertThatUnchanged(later, "20");
        assertThatUnchanged(earlier, "20");
    }

    @Test
    void shouldAllocateAcrossMultipleBatches() {
        var itemId = UUID.randomUUID();
        var a = batch(itemId, "00000000-0000-0000-0000-000000000001", "15.000", "2026-09-01", "2026-08-20");
        var b = batch(itemId, "00000000-0000-0000-0000-000000000002", "25.000", "2026-09-02", "2026-08-21");
        var c = batch(itemId, "00000000-0000-0000-0000-000000000003", "100.000", "2026-09-03", "2026-08-22");

        var plan = policy.allocate(itemId, List.of(c, b, a), decimal("80.000"), TODAY);

        assertThat(plan.requestedQuantity()).isEqualByComparingTo("80.000");
        assertThat(plan.allocatedQuantity()).isEqualByComparingTo("80.000");
        assertThat(plan.allocations()).extracting(BatchAllocation::batchId)
            .containsExactly(a.getId(), b.getId(), c.getId());
        assertThat(plan.allocations()).extracting(BatchAllocation::quantity)
            .allSatisfy(quantity -> assertThat(quantity).isPositive());
        assertThat(plan.allocations().get(0).quantity()).isEqualByComparingTo("15.000");
        assertThat(plan.allocations().get(1).quantity()).isEqualByComparingTo("25.000");
        assertThat(plan.allocations().get(2).quantity()).isEqualByComparingTo("40.000");
        assertThatUnchanged(a, "15.000");
        assertThatUnchanged(b, "25.000");
        assertThatUnchanged(c, "100.000");
    }

    @Test
    void shouldUseSmallerRemainingQuantityAsFirstTieBreaker() {
        var itemId = UUID.randomUUID();
        var larger = batch(itemId, "00000000-0000-0000-0000-000000000002", "20", "2026-09-01", "2026-08-20");
        var smaller = batch(itemId, "00000000-0000-0000-0000-000000000001", "10", "2026-09-01", "2026-08-22");

        var plan = policy.allocate(itemId, List.of(larger, smaller), decimal("10"), TODAY);

        assertThat(plan.allocations().getFirst().batchId()).isEqualTo(smaller.getId());
    }

    @Test
    void shouldUseEarlierReceivedAtAsSecondTieBreaker() {
        var itemId = UUID.randomUUID();
        var newer = batch(itemId, "00000000-0000-0000-0000-000000000002", "20", "2026-09-01", "2026-08-21");
        var older = batch(itemId, "00000000-0000-0000-0000-000000000001", "20", "2026-09-01", "2026-08-20");

        var plan = policy.allocate(itemId, List.of(newer, older), decimal("10"), TODAY);

        assertThat(plan.allocations().getFirst().batchId()).isEqualTo(older.getId());
    }

    @Test
    void shouldUseUuidAsFinalDeterministicTieBreaker() {
        var itemId = UUID.randomUUID();
        var laterId = batch(itemId, "00000000-0000-0000-0000-000000000002", "20", "2026-09-01", "2026-08-20");
        var earlierId = batch(itemId, "00000000-0000-0000-0000-000000000001", "20", "2026-09-01", "2026-08-20");

        var plan = policy.allocate(itemId, List.of(laterId, earlierId), decimal("10"), TODAY);

        assertThat(plan.allocations().getFirst().batchId()).isEqualTo(earlierId.getId());
    }

    @Test
    void shouldExcludeBatchExpiringToday() {
        assertIneligibleBatch(TODAY);
    }

    @Test
    void shouldExcludeAlreadyExpiredBatch() {
        assertIneligibleBatch(TODAY.minusDays(1));
    }

    @Test
    void shouldExcludeBatchWithoutExpirationDate() {
        var itemId = UUID.randomUUID();
        var noExpiry = batch(itemId, UUID.randomUUID().toString(), "10", null, "2026-08-20");

        assertInsufficient(itemId, List.of(noExpiry), "1", "0");
        assertThatUnchanged(noExpiry, "10");
    }

    @Test
    void shouldExcludeZeroBalanceBatch() {
        var itemId = UUID.randomUUID();
        var zeroBalance = batch(itemId, UUID.randomUUID().toString(), "0", "2026-09-01", "2026-08-20");

        assertInsufficient(itemId, List.of(zeroBalance), "1", "0");
        assertThatUnchanged(zeroBalance, "0");
    }

    @Test
    void shouldIgnoreNullCandidatesSafely() {
        var itemId = UUID.randomUUID();
        var eligible = batch(itemId, UUID.randomUUID().toString(), "10", "2026-09-01", "2026-08-20");

        var plan = policy.allocate(itemId, java.util.Arrays.asList(null, eligible), decimal("10"), TODAY);

        assertThat(plan.allocations()).singleElement()
            .extracting(BatchAllocation::batchId).isEqualTo(eligible.getId());
        assertThatUnchanged(eligible, "10");
    }

    @Test
    void shouldRejectWhenEligibleStockIsInsufficient() {
        var itemId = UUID.randomUUID();
        var eligible = batch(itemId, UUID.randomUUID().toString(), "55.000", "2026-09-01", "2026-08-20");
        var expired = batch(itemId, UUID.randomUUID().toString(), "20.000", "2026-08-24", "2026-08-20");
        var expiresToday = batch(itemId, UUID.randomUUID().toString(), "10.000", "2026-08-25", "2026-08-20");

        assertThatThrownBy(() -> policy.allocate(itemId, List.of(eligible, expired, expiresToday), decimal("80.000"), TODAY))
            .isInstanceOf(InsufficientEligibleStockException.class)
            .satisfies(exception -> {
                var insufficient = (InsufficientEligibleStockException) exception;
                assertThat(insufficient.getInventoryItemId()).isEqualTo(itemId);
                assertThat(insufficient.getRequestedQuantity()).isEqualByComparingTo("80.000");
                assertThat(insufficient.getAvailableQuantity()).isEqualByComparingTo("55.000");
                assertThat(insufficient.getCode()).isEqualTo("INSUFFICIENT_ELIGIBLE_STOCK");
                assertThat(insufficient.getErrorType()).isEqualTo(ErrorType.BUSINESS_RULE);
            });
        assertThatUnchanged(eligible, "55.000");
        assertThatUnchanged(expired, "20.000");
        assertThatUnchanged(expiresToday, "10.000");
    }

    @Test
    void shouldCalculateAvailableQuantityUsingOnlyEligibleBatches() {
        var itemId = UUID.randomUUID();
        var valid = batch(itemId, UUID.randomUUID().toString(), "12.500", "2026-09-01", "2026-08-20");
        var expiresToday = batch(itemId, UUID.randomUUID().toString(), "40", "2026-08-25", "2026-08-20");
        var expired = batch(itemId, UUID.randomUUID().toString(), "40", "2026-08-24", "2026-08-20");
        var noExpiry = batch(itemId, UUID.randomUUID().toString(), "40", null, "2026-08-20");
        var empty = batch(itemId, UUID.randomUUID().toString(), "0", "2026-09-02", "2026-08-20");

        assertInsufficient(itemId, List.of(valid, expiresToday, expired, noExpiry, empty), "13", "12.500");
    }

    @Test
    void shouldPreserveBigDecimalPrecision() {
        var itemId = UUID.randomUUID();
        var a = batch(itemId, UUID.randomUUID().toString(), "0.100", "2026-09-01", "2026-08-20");
        var b = batch(itemId, UUID.randomUUID().toString(), "0.200", "2026-09-02", "2026-08-21");

        var plan = policy.allocate(itemId, List.of(a, b), decimal("0.300"), TODAY);

        assertThat(plan.allocatedQuantity()).isEqualByComparingTo("0.300");
        assertThat(plan.allocations().get(0).quantity()).isEqualByComparingTo("0.100");
        assertThat(plan.allocations().get(1).quantity()).isEqualByComparingTo("0.200");
        assertThatUnchanged(a, "0.100");
        assertThatUnchanged(b, "0.200");
    }

    @Test
    void shouldAcceptRequestedQuantityExactlyEqualToEligibleStock() {
        var itemId = UUID.randomUUID();
        var batch = batch(itemId, UUID.randomUUID().toString(), "55.000", "2026-09-01", "2026-08-20");

        var plan = policy.allocate(itemId, List.of(batch), decimal("55.000"), TODAY);

        assertThat(plan.allocatedQuantity()).isEqualByComparingTo("55.000");
        assertThat(plan.allocations()).singleElement()
            .satisfies(allocation -> assertThat(allocation.quantity()).isEqualByComparingTo("55.000"));
        assertThatUnchanged(batch, "55.000");
    }

    @Test
    void shouldRejectNullInventoryItemId() {
        assertThatIllegalArgumentException().isThrownBy(() -> policy.allocate(null, List.of(), decimal("1"), TODAY));
    }

    @Test
    void shouldRejectNullCandidateList() {
        assertThatIllegalArgumentException().isThrownBy(() -> policy.allocate(UUID.randomUUID(), null, decimal("1"), TODAY));
    }

    @Test
    void shouldRejectNullOrNonPositiveRequestedQuantity() {
        var itemId = UUID.randomUUID();
        assertThatIllegalArgumentException().isThrownBy(() -> policy.allocate(itemId, List.of(), null, TODAY));
        assertThatIllegalArgumentException().isThrownBy(() -> policy.allocate(itemId, List.of(), BigDecimal.ZERO, TODAY));
        assertThatIllegalArgumentException().isThrownBy(() -> policy.allocate(itemId, List.of(), decimal("-1"), TODAY));
    }

    @Test
    void shouldRejectNullToday() {
        assertThatIllegalArgumentException().isThrownBy(() -> policy.allocate(UUID.randomUUID(), List.of(), decimal("1"), null));
    }

    private void assertIneligibleBatch(LocalDate expiresAt) {
        var itemId = UUID.randomUUID();
        var batch = batch(itemId, UUID.randomUUID().toString(), "10", expiresAt.toString(), "2026-08-20");

        assertInsufficient(itemId, List.of(batch), "1", "0");
        assertThatUnchanged(batch, "10");
    }

    private void assertInsufficient(UUID itemId, List<Batch> batches, String requested, String available) {
        assertThatThrownBy(() -> policy.allocate(itemId, batches, decimal(requested), TODAY))
            .isInstanceOf(InsufficientEligibleStockException.class)
            .satisfies(exception -> assertThat(((InsufficientEligibleStockException) exception).getAvailableQuantity())
                .isEqualByComparingTo(available));
    }

    private static Batch batch(UUID itemId, String batchId, String quantity, String expiresAt, String receivedAt) {
        var currentQuantity = decimal(quantity);
        return new Batch(
            UUID.fromString(batchId), itemId, null, "LOT-" + batchId,
            currentQuantity.signum() == 0 ? BigDecimal.ONE : currentQuantity,
            currentQuantity,
            LocalDate.parse(receivedAt),
            expiresAt == null ? null : LocalDate.parse(expiresAt)
        );
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private static void assertThatUnchanged(Batch batch, String quantity) {
        assertThat(batch.getCurrentQuantity()).isEqualByComparingTo(quantity);
    }
}
