package com.ceudelavanda.lavandaflow.inventory.infrastructure.web;

import com.ceudelavanda.lavandaflow.inventory.application.RegisterStockEntry;
import com.ceudelavanda.lavandaflow.inventory.application.RegisterStockAdjustment;
import com.ceudelavanda.lavandaflow.inventory.application.RegisterStockWithdrawal;
import com.ceudelavanda.lavandaflow.inventory.application.command.RegisterStockEntryCommand;
import com.ceudelavanda.lavandaflow.inventory.application.command.RegisterStockAdjustmentCommand;
import com.ceudelavanda.lavandaflow.inventory.application.command.RegisterStockWithdrawalCommand;
import com.ceudelavanda.lavandaflow.inventory.application.result.StockMovementResult;
import com.ceudelavanda.lavandaflow.inventory.domain.MovementType;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.BatchNotFoundException;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InsufficientStockException;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InvalidStockAdjustmentException;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InventoryController.class)
@Import(ClockConfig.class)
@WithMockUser
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegisterStockEntry registerStockEntry;

    @MockitoBean
    private RegisterStockAdjustment registerStockAdjustment;

    @MockitoBean
    private RegisterStockWithdrawal registerStockWithdrawal;

    @Test
    void shouldRegisterStockEntry() throws Exception {
        var batchId = UUID.randomUUID();
        var movementId = UUID.randomUUID();
        var occurredAt = Instant.parse("2026-08-25T16:00:00Z");

        var result = new StockMovementResult(
            movementId,
            batchId,
            MovementType.ENTRY,
            new BigDecimal("50"),
            new BigDecimal("150"),
            "Supplier replenishment",
            occurredAt
        );

        when(registerStockEntry.execute(any(RegisterStockEntryCommand.class)))
            .thenReturn(result);

        mockMvc.perform(post("/api/v1/inventory/batches/{batchId}/entries", batchId)
                .contentType("application/json")
                .with(csrf())
                .content("""
                    {
                      "quantity": 50,
                      "reason": "Supplier replenishment"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.movementId").value(movementId.toString()))
            .andExpect(jsonPath("$.batchId").value(batchId.toString()))
            .andExpect(jsonPath("$.type").value("ENTRY"))
            .andExpect(jsonPath("$.quantity").value(50))
            .andExpect(jsonPath("$.resultingBalance").value(150))
            .andExpect(jsonPath("$.reason").value("Supplier replenishment"))
            .andExpect(jsonPath("$.occurredAt").value("2026-08-25T16:00:00Z"));

        var captor = org.mockito.ArgumentCaptor
            .forClass(RegisterStockEntryCommand.class);

        verify(registerStockEntry).execute(captor.capture());

        var command = captor.getValue();

        assertThat(command.batchId()).isEqualTo(batchId);
        assertThat(command.quantity()).isEqualByComparingTo("50");
        assertThat(command.reason()).isEqualTo("Supplier replenishment");
    }

    @Test
    void shouldRejectZeroQuantity() throws Exception {
        var batchId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/inventory/batches/{batchId}/entries", batchId)
                .contentType("application/json")
                .with(csrf())
                .content("""
                    {
                      "quantity": 0
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value("Request validation failed"))
            .andExpect(jsonPath("$.path")
                .value("/api/v1/inventory/batches/" + batchId + "/entries"))
            .andExpect(jsonPath("$.details.quantity").exists())
            .andExpect(jsonPath("$.timestamp").exists());

        verify(registerStockEntry, never())
            .execute(any(RegisterStockEntryCommand.class));
    }

    @Test
    void shouldRejectNegativeQuantity() throws Exception {
        var batchId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/inventory/batches/{batchId}/entries", batchId)
                .contentType("application/json")
                .with(csrf())
                .content("""
                    {
                      "quantity": -10
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value("Request validation failed"))
            .andExpect(jsonPath("$.path")
                .value("/api/v1/inventory/batches/" + batchId + "/entries"))
            .andExpect(jsonPath("$.details.quantity").exists())
            .andExpect(jsonPath("$.timestamp").exists());

        verify(registerStockEntry, never())
            .execute(any(RegisterStockEntryCommand.class));
    }

    @Test
    void shouldRejectMissingQuantity() throws Exception {
        var batchId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/inventory/batches/{batchId}/entries", batchId)
                .contentType("application/json")
                .with(csrf())
                .content("""
                    {
                      "reason": "Supplier replenishment"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value("Request validation failed"))
            .andExpect(jsonPath("$.path")
                .value("/api/v1/inventory/batches/" + batchId + "/entries"))
            .andExpect(jsonPath("$.details.quantity").exists())
            .andExpect(jsonPath("$.timestamp").exists());

        verify(registerStockEntry, never())
            .execute(any(RegisterStockEntryCommand.class));
    }

    @Test
    void shouldRejectReasonLongerThanMaximumLength() throws Exception {
        var batchId = UUID.randomUUID();
        var reason = "a".repeat(256);

        mockMvc.perform(post("/api/v1/inventory/batches/{batchId}/entries", batchId)
                .contentType("application/json")
                .with(csrf())
                .content("""
                    {
                      "quantity": 50,
                      "reason": "%s"
                    }
                    """.formatted(reason)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value("Request validation failed"))
            .andExpect(jsonPath("$.path")
                .value("/api/v1/inventory/batches/" + batchId + "/entries"))
            .andExpect(jsonPath("$.details.reason").exists())
            .andExpect(jsonPath("$.timestamp").exists());

        verify(registerStockEntry, never())
            .execute(any(RegisterStockEntryCommand.class));
    }

    @Test
    void shouldReturnNotFoundWhenBatchDoesNotExist() throws Exception {
        var batchId = UUID.randomUUID();

        when(registerStockEntry.execute(any(RegisterStockEntryCommand.class)))
            .thenThrow(new BatchNotFoundException(batchId));

        mockMvc.perform(post("/api/v1/inventory/batches/{batchId}/entries", batchId)
                .contentType("application/json")
                .with(csrf())
                .content("""
                    {
                      "quantity": 50,
                      "reason": "Supplier replenishment"
                    }
                    """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.error").value("Not Found"))
            .andExpect(jsonPath("$.code").value("BATCH_NOT_FOUND"))
            .andExpect(jsonPath("$.message")
                .value("Batch not found: " + batchId))
            .andExpect(jsonPath("$.path")
                .value("/api/v1/inventory/batches/" + batchId + "/entries"))
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void shouldRegisterStockWithdrawal() throws Exception {
        var batchId = UUID.randomUUID();
        var movementId = UUID.randomUUID();
        var occurredAt = Instant.parse("2026-08-25T16:00:00Z");

        var result = new StockMovementResult(
            movementId,
            batchId,
            MovementType.CONSUMPTION,
            new BigDecimal("50"),
            new BigDecimal("50"),
            "Inventory use",
            occurredAt
        );

        when(registerStockWithdrawal.execute(any(RegisterStockWithdrawalCommand.class)))
            .thenReturn(result);

        mockMvc.perform(post("/api/v1/inventory/batches/{batchId}/withdrawals", batchId)
                .contentType("application/json")
                .with(csrf())
                .content("""
                    {
                      "quantity": 50,
                      "reason": "Inventory use"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.movementId").value(movementId.toString()))
            .andExpect(jsonPath("$.batchId").value(batchId.toString()))
            .andExpect(jsonPath("$.type").value("CONSUMPTION"))
            .andExpect(jsonPath("$.quantity").value(50))
            .andExpect(jsonPath("$.resultingBalance").value(50))
            .andExpect(jsonPath("$.reason").value("Inventory use"))
            .andExpect(jsonPath("$.occurredAt").value("2026-08-25T16:00:00Z"));

        var captor = org.mockito.ArgumentCaptor
            .forClass(RegisterStockWithdrawalCommand.class);

        verify(registerStockWithdrawal).execute(captor.capture());

        var command = captor.getValue();

        assertThat(command.batchId()).isEqualTo(batchId);
        assertThat(command.quantity()).isEqualByComparingTo("50");
        assertThat(command.reason()).isEqualTo("Inventory use");
    }

    @Test
    void shouldRejectZeroQuantityForStockWithdrawal() throws Exception {
        var batchId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/inventory/batches/{batchId}/withdrawals", batchId)
                .contentType("application/json")
                .with(csrf())
                .content("""
                    {
                      "quantity": 0
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value("Request validation failed"))
            .andExpect(jsonPath("$.path")
                .value("/api/v1/inventory/batches/" + batchId + "/withdrawals"))
            .andExpect(jsonPath("$.details.quantity").exists())
            .andExpect(jsonPath("$.timestamp").exists());

        verify(registerStockWithdrawal, never())
            .execute(any(RegisterStockWithdrawalCommand.class));
    }

    @Test
    void shouldRejectNegativeQuantityForStockWithdrawal() throws Exception {
        var batchId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/inventory/batches/{batchId}/withdrawals", batchId)
                .contentType("application/json")
                .with(csrf())
                .content("""
                    {
                      "quantity": -10
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value("Request validation failed"))
            .andExpect(jsonPath("$.path")
                .value("/api/v1/inventory/batches/" + batchId + "/withdrawals"))
            .andExpect(jsonPath("$.details.quantity").exists())
            .andExpect(jsonPath("$.timestamp").exists());

        verify(registerStockWithdrawal, never())
            .execute(any(RegisterStockWithdrawalCommand.class));
    }

    @Test
    void shouldRejectMissingQuantityForStockWithdrawal() throws Exception {
        var batchId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/inventory/batches/{batchId}/withdrawals", batchId)
                .contentType("application/json")
                .with(csrf())
                .content("""
                    {
                      "reason": "Inventory use"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value("Request validation failed"))
            .andExpect(jsonPath("$.path")
                .value("/api/v1/inventory/batches/" + batchId + "/withdrawals"))
            .andExpect(jsonPath("$.details.quantity").exists())
            .andExpect(jsonPath("$.timestamp").exists());

        verify(registerStockWithdrawal, never())
            .execute(any(RegisterStockWithdrawalCommand.class));
    }

    @Test
    void shouldRejectReasonLongerThanMaximumLengthForStockWithdrawal() throws Exception {
        var batchId = UUID.randomUUID();
        var reason = "a".repeat(256);

        mockMvc.perform(post("/api/v1/inventory/batches/{batchId}/withdrawals", batchId)
                .contentType("application/json")
                .with(csrf())
                .content("""
                    {
                      "quantity": 50,
                      "reason": "%s"
                    }
                    """.formatted(reason)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value("Request validation failed"))
            .andExpect(jsonPath("$.path")
                .value("/api/v1/inventory/batches/" + batchId + "/withdrawals"))
            .andExpect(jsonPath("$.details.reason").exists())
            .andExpect(jsonPath("$.timestamp").exists());

        verify(registerStockWithdrawal, never())
            .execute(any(RegisterStockWithdrawalCommand.class));
    }

    @Test
    void shouldReturnNotFoundWhenWithdrawalBatchDoesNotExist() throws Exception {
        var batchId = UUID.randomUUID();

        when(registerStockWithdrawal.execute(any(RegisterStockWithdrawalCommand.class)))
            .thenThrow(new BatchNotFoundException(batchId));

        mockMvc.perform(post("/api/v1/inventory/batches/{batchId}/withdrawals", batchId)
                .contentType("application/json")
                .with(csrf())
                .content("""
                    {
                      "quantity": 50,
                      "reason": "Inventory use"
                    }
                    """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.error").value("Not Found"))
            .andExpect(jsonPath("$.code").value("BATCH_NOT_FOUND"))
            .andExpect(jsonPath("$.message")
                .value("Batch not found: " + batchId))
            .andExpect(jsonPath("$.path")
                .value("/api/v1/inventory/batches/" + batchId + "/withdrawals"))
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void shouldReturnUnprocessableContentWhenStockIsInsufficient() throws Exception {
        var batchId = UUID.randomUUID();

        when(registerStockWithdrawal.execute(any(RegisterStockWithdrawalCommand.class)))
            .thenThrow(new InsufficientStockException(
                batchId,
                new BigDecimal("150"),
                new BigDecimal("100")
            ));

        mockMvc.perform(post("/api/v1/inventory/batches/{batchId}/withdrawals", batchId)
                .contentType("application/json")
                .with(csrf())
                .content("""
                    {
                      "quantity": 150,
                      "reason": "Inventory use"
                    }
                    """))
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.status").value(422))
            .andExpect(jsonPath("$.error").value("Unprocessable Content"))
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
            .andExpect(jsonPath("$.message")
                .value("Insufficient stock for batch " + batchId))
            .andExpect(jsonPath("$.path")
                .value("/api/v1/inventory/batches/" + batchId + "/withdrawals"))
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void shouldRegisterPositiveStockAdjustment() throws Exception {
        var batchId = UUID.randomUUID();
        var movementId = UUID.randomUUID();
        var occurredAt = Instant.parse("2026-08-25T16:00:00Z");

        var result = new StockMovementResult(
            movementId,
            batchId,
            MovementType.ADJUSTMENT_IN,
            new BigDecimal("25"),
            new BigDecimal("125"),
            "Physical count correction",
            occurredAt
        );

        when(registerStockAdjustment.execute(any(RegisterStockAdjustmentCommand.class)))
            .thenReturn(result);

        mockMvc.perform(post("/api/v1/inventory/batches/{batchId}/adjustments", batchId)
                .contentType("application/json")
                .with(csrf())
                .content("""
                    {
                      "quantity": 25,
                      "reason": "Physical count correction"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.movementId").value(movementId.toString()))
            .andExpect(jsonPath("$.batchId").value(batchId.toString()))
            .andExpect(jsonPath("$.type").value("ADJUSTMENT_IN"))
            .andExpect(jsonPath("$.quantity").value(25))
            .andExpect(jsonPath("$.resultingBalance").value(125))
            .andExpect(jsonPath("$.reason").value("Physical count correction"))
            .andExpect(jsonPath("$.occurredAt").value("2026-08-25T16:00:00Z"));

        var captor = org.mockito.ArgumentCaptor
            .forClass(RegisterStockAdjustmentCommand.class);

        verify(registerStockAdjustment).execute(captor.capture());

        var command = captor.getValue();

        assertThat(command.batchId()).isEqualTo(batchId);
        assertThat(command.quantity()).isEqualByComparingTo("25");
        assertThat(command.reason()).isEqualTo("Physical count correction");
    }

    @Test
    void shouldRegisterNegativeStockAdjustmentWithPositiveMovementQuantity() throws Exception {
        var batchId = UUID.randomUUID();
        var movementId = UUID.randomUUID();
        var occurredAt = Instant.parse("2026-08-25T16:00:00Z");

        var result = new StockMovementResult(
            movementId,
            batchId,
            MovementType.ADJUSTMENT_OUT,
            new BigDecimal("25"),
            new BigDecimal("75"),
            "Physical count correction",
            occurredAt
        );

        when(registerStockAdjustment.execute(any(RegisterStockAdjustmentCommand.class)))
            .thenReturn(result);

        mockMvc.perform(post("/api/v1/inventory/batches/{batchId}/adjustments", batchId)
                .contentType("application/json")
                .with(csrf())
                .content("""
                    {
                      "quantity": -25,
                      "reason": "Physical count correction"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.movementId").value(movementId.toString()))
            .andExpect(jsonPath("$.batchId").value(batchId.toString()))
            .andExpect(jsonPath("$.type").value("ADJUSTMENT_OUT"))
            .andExpect(jsonPath("$.quantity").value(25))
            .andExpect(jsonPath("$.resultingBalance").value(75))
            .andExpect(jsonPath("$.reason").value("Physical count correction"))
            .andExpect(jsonPath("$.occurredAt").value("2026-08-25T16:00:00Z"));

        var captor = org.mockito.ArgumentCaptor
            .forClass(RegisterStockAdjustmentCommand.class);

        verify(registerStockAdjustment).execute(captor.capture());

        assertThat(captor.getValue().quantity()).isEqualByComparingTo("-25");
    }

    @Test
    void shouldRejectMissingQuantityForStockAdjustment() throws Exception {
        assertInvalidAdjustmentRequest(
            """
                {
                  "reason": "Physical count correction"
                }
                """,
            "quantity"
        );
    }

    @Test
    void shouldRejectMissingReasonForStockAdjustment() throws Exception {
        assertInvalidAdjustmentRequest(
            """
                {
                  "quantity": 25
                }
                """,
            "reason"
        );
    }

    @Test
    void shouldRejectBlankReasonForStockAdjustment() throws Exception {
        assertInvalidAdjustmentRequest(
            """
                {
                  "quantity": 25,
                  "reason": ""
                }
                """,
            "reason"
        );
    }

    @Test
    void shouldRejectWhitespaceOnlyReasonForStockAdjustment() throws Exception {
        assertInvalidAdjustmentRequest(
            """
                {
                  "quantity": 25,
                  "reason": "   "
                }
                """,
            "reason"
        );
    }

    @Test
    void shouldRejectReasonLongerThanMaximumLengthForStockAdjustment() throws Exception {
        var reason = "a".repeat(256);

        assertInvalidAdjustmentRequest(
            """
                {
                  "quantity": 25,
                  "reason": "%s"
                }
                """.formatted(reason),
            "reason"
        );
    }

    @Test
    void shouldReturnBadRequestWhenStockAdjustmentIsZero() throws Exception {
        var batchId = UUID.randomUUID();

        when(registerStockAdjustment.execute(any(RegisterStockAdjustmentCommand.class)))
            .thenThrow(new InvalidStockAdjustmentException());

        mockMvc.perform(post("/api/v1/inventory/batches/{batchId}/adjustments", batchId)
                .contentType("application/json")
                .with(csrf())
                .content("""
                    {
                      "quantity": 0,
                      "reason": "Physical count correction"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.code").value("INVALID_STOCK_ADJUSTMENT"))
            .andExpect(jsonPath("$.message").value("Stock adjustment must not be zero"))
            .andExpect(jsonPath("$.path")
                .value("/api/v1/inventory/batches/" + batchId + "/adjustments"))
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void shouldReturnNotFoundWhenAdjustmentBatchDoesNotExist() throws Exception {
        var batchId = UUID.randomUUID();

        when(registerStockAdjustment.execute(any(RegisterStockAdjustmentCommand.class)))
            .thenThrow(new BatchNotFoundException(batchId));

        mockMvc.perform(post("/api/v1/inventory/batches/{batchId}/adjustments", batchId)
                .contentType("application/json")
                .with(csrf())
                .content("""
                    {
                      "quantity": 25,
                      "reason": "Physical count correction"
                    }
                    """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.error").value("Not Found"))
            .andExpect(jsonPath("$.code").value("BATCH_NOT_FOUND"))
            .andExpect(jsonPath("$.message")
                .value("Batch not found: " + batchId))
            .andExpect(jsonPath("$.path")
                .value("/api/v1/inventory/batches/" + batchId + "/adjustments"))
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void shouldReturnUnprocessableContentWhenAdjustmentExceedsStock() throws Exception {
        var batchId = UUID.randomUUID();

        when(registerStockAdjustment.execute(any(RegisterStockAdjustmentCommand.class)))
            .thenThrow(new InsufficientStockException(
                batchId,
                new BigDecimal("150"),
                new BigDecimal("100")
            ));

        mockMvc.perform(post("/api/v1/inventory/batches/{batchId}/adjustments", batchId)
                .contentType("application/json")
                .with(csrf())
                .content("""
                    {
                      "quantity": -150,
                      "reason": "Physical count correction"
                    }
                    """))
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.status").value(422))
            .andExpect(jsonPath("$.error").value("Unprocessable Content"))
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
            .andExpect(jsonPath("$.message")
                .value("Insufficient stock for batch " + batchId))
            .andExpect(jsonPath("$.path")
                .value("/api/v1/inventory/batches/" + batchId + "/adjustments"))
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.details").doesNotExist());
    }

    private void assertInvalidAdjustmentRequest(String requestBody, String field) throws Exception {
        var batchId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/inventory/batches/{batchId}/adjustments", batchId)
                .contentType("application/json")
                .with(csrf())
                .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value("Request validation failed"))
            .andExpect(jsonPath("$.path")
                .value("/api/v1/inventory/batches/" + batchId + "/adjustments"))
            .andExpect(jsonPath("$.details." + field).exists())
            .andExpect(jsonPath("$.timestamp").exists());

        verify(registerStockAdjustment, never())
            .execute(any(RegisterStockAdjustmentCommand.class));
    }
}
