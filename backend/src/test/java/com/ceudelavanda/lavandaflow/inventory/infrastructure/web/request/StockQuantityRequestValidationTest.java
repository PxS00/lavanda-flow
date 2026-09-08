package com.ceudelavanda.lavandaflow.inventory.infrastructure.web.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StockQuantityRequestValidationTest {

    private final Validator validator = Validation
        .buildDefaultValidatorFactory()
        .getValidator();

    @Test
    void shouldAcceptSmallestSupportedQuantityAcrossStockRequests() {
        var quantity = new BigDecimal("0.000001");

        assertValid(new RegisterStockEntryRequest(quantity, null));
        assertValid(new RegisterStockWithdrawalRequest(quantity, null));
        assertValid(new RegisterStockAdjustmentRequest(quantity, "Inventory count"));
        assertValid(new RegisterStockLossRequest(quantity, "Damaged"));
        assertValid(new RegisterExpiredStockDisposalRequest(quantity, "Discarded"));
        assertValid(new RegisterFefoWithdrawalRequest(quantity, null));
    }

    @Test
    void shouldRejectExcessFractionDigitsAcrossStockRequests() {
        var quantity = new BigDecimal("1.1234567");

        assertInvalidQuantity(new RegisterStockEntryRequest(quantity, null));
        assertInvalidQuantity(new RegisterStockWithdrawalRequest(quantity, null));
        assertInvalidQuantity(new RegisterStockAdjustmentRequest(quantity, "Inventory count"));
        assertInvalidQuantity(new RegisterStockLossRequest(quantity, "Damaged"));
        assertInvalidQuantity(new RegisterExpiredStockDisposalRequest(quantity, "Discarded"));
        assertInvalidQuantity(new RegisterFefoWithdrawalRequest(quantity, null));
    }

    @Test
    void shouldRejectIntegerPrecisionOverflowAcrossStockRequests() {
        var quantity = new BigDecimal("10000000000000");

        assertInvalidQuantity(new RegisterStockEntryRequest(quantity, null));
        assertInvalidQuantity(new RegisterStockWithdrawalRequest(quantity, null));
        assertInvalidQuantity(new RegisterStockAdjustmentRequest(quantity, "Inventory count"));
        assertInvalidQuantity(new RegisterStockLossRequest(quantity, "Damaged"));
        assertInvalidQuantity(new RegisterExpiredStockDisposalRequest(quantity, "Discarded"));
        assertInvalidQuantity(new RegisterFefoWithdrawalRequest(quantity, null));
    }

    @Test
    void shouldRequireNonBlankReasonWithinMaximumLengthForMaintenanceRequests() {
        assertInvalidReason(new RegisterStockLossRequest(BigDecimal.ONE, null));
        assertInvalidReason(new RegisterStockLossRequest(BigDecimal.ONE, "  "));
        assertInvalidReason(new RegisterStockLossRequest(BigDecimal.ONE, "a".repeat(256)));
        assertInvalidReason(new RegisterExpiredStockDisposalRequest(BigDecimal.ONE, null));
        assertInvalidReason(new RegisterExpiredStockDisposalRequest(BigDecimal.ONE, "  "));
        assertInvalidReason(new RegisterExpiredStockDisposalRequest(BigDecimal.ONE, "a".repeat(256)));
    }

    private void assertValid(Object request) {
        assertThat(validator.validate(request)).isEmpty();
    }

    private void assertInvalidQuantity(Object request) {
        assertThat(validator.validate(request))
            .anySatisfy(violation ->
                assertThat(violation.getPropertyPath().toString()).isEqualTo("quantity")
            );
    }

    private void assertInvalidReason(Object request) {
        assertThat(validator.validate(request))
            .anySatisfy(violation ->
                assertThat(violation.getPropertyPath().toString()).isEqualTo("reason")
            );
    }
}
