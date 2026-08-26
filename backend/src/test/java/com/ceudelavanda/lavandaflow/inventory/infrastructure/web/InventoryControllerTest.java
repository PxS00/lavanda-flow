package com.ceudelavanda.lavandaflow.inventory.infrastructure.web;

import com.ceudelavanda.lavandaflow.inventory.application.RegisterFefoWithdrawal;
import com.ceudelavanda.lavandaflow.inventory.application.RegisterStockAdjustment;
import com.ceudelavanda.lavandaflow.inventory.application.RegisterStockEntry;
import com.ceudelavanda.lavandaflow.inventory.application.RegisterStockWithdrawal;
import com.ceudelavanda.lavandaflow.inventory.application.command.RegisterFefoWithdrawalCommand;
import com.ceudelavanda.lavandaflow.inventory.application.command.RegisterStockAdjustmentCommand;
import com.ceudelavanda.lavandaflow.inventory.application.command.RegisterStockEntryCommand;
import com.ceudelavanda.lavandaflow.inventory.application.command.RegisterStockWithdrawalCommand;
import com.ceudelavanda.lavandaflow.inventory.application.result.FefoWithdrawalAllocationResult;
import com.ceudelavanda.lavandaflow.inventory.application.result.FefoWithdrawalResult;
import com.ceudelavanda.lavandaflow.inventory.application.result.StockMovementResult;
import com.ceudelavanda.lavandaflow.inventory.domain.MovementType;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.*;
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

    @MockitoBean
    private RegisterFefoWithdrawal registerFefoWithdrawal;

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

    @Test
    void shouldReturn201ForFefoWithdrawal() throws Exception {
        var itemId = UUID.randomUUID();
        var firstBatchId = UUID.randomUUID();
        var secondBatchId = UUID.randomUUID();
        var firstMovementId = UUID.randomUUID();
        var secondMovementId = UUID.randomUUID();
        when(registerFefoWithdrawal.execute(any(RegisterFefoWithdrawalCommand.class)))
            .thenReturn(new FefoWithdrawalResult(
                itemId,
                new BigDecimal("80.000"),
                new BigDecimal("80.000"),
                java.util.List.of(
                    new FefoWithdrawalAllocationResult(firstBatchId, firstMovementId, new BigDecimal("15.000")),
                    new FefoWithdrawalAllocationResult(secondBatchId, secondMovementId, new BigDecimal("65.000"))
                )
            ));

        mockMvc.perform(fefoWithdrawal(itemId, """
            {
              "quantity": 80.000,
              "reason": "Production"
            }
            """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.inventoryItemId").value(itemId.toString()))
            .andExpect(jsonPath("$.requestedQuantity").value(80.000))
            .andExpect(jsonPath("$.allocatedQuantity").value(80.000))
            .andExpect(jsonPath("$.allocations[0].batchId").value(firstBatchId.toString()))
            .andExpect(jsonPath("$.allocations[0].movementId").value(firstMovementId.toString()))
            .andExpect(jsonPath("$.allocations[0].quantity").value(15.000))
            .andExpect(jsonPath("$.allocations[1].batchId").value(secondBatchId.toString()))
            .andExpect(jsonPath("$.allocations[1].movementId").value(secondMovementId.toString()))
            .andExpect(jsonPath("$.allocations[1].quantity").value(65.000));

        var captor = org.mockito.ArgumentCaptor.forClass(RegisterFefoWithdrawalCommand.class);
        verify(registerFefoWithdrawal).execute(captor.capture());
        assertThat(captor.getValue().inventoryItemId()).isEqualTo(itemId);
        assertThat(captor.getValue().quantity()).isEqualByComparingTo("80.000");
        assertThat(captor.getValue().reason()).isEqualTo("Production");
    }

    @Test
    void shouldReturn400WhenFefoQuantityIsMissing() throws Exception {
        assertInvalidFefoRequest("{}", "quantity");
    }

    @Test
    void shouldReturn400WhenFefoQuantityIsZero() throws Exception {
        assertInvalidFefoRequest("{ \"quantity\": 0 }", "quantity");
    }

    @Test
    void shouldReturn400WhenFefoQuantityIsNegative() throws Exception {
        assertInvalidFefoRequest("{ \"quantity\": -1 }", "quantity");
    }

    @Test
    void shouldReturn400WhenFefoReasonExceeds255Characters() throws Exception {
        assertInvalidFefoRequest("""
            {
              "quantity": 1,
              "reason": "%s"
            }
            """.formatted("a".repeat(256)), "reason");
    }

    @Test
    void shouldAcceptMissingFefoReason() throws Exception {
        var itemId = UUID.randomUUID();
        when(registerFefoWithdrawal.execute(any(RegisterFefoWithdrawalCommand.class)))
            .thenReturn(new FefoWithdrawalResult(itemId, BigDecimal.ONE, BigDecimal.ONE, java.util.List.of()));

        mockMvc.perform(fefoWithdrawal(itemId, "{ \"quantity\": 1 }"))
            .andExpect(status().isCreated());

        var captor = org.mockito.ArgumentCaptor.forClass(RegisterFefoWithdrawalCommand.class);
        verify(registerFefoWithdrawal).execute(captor.capture());
        assertThat(captor.getValue().reason()).isNull();
    }

    @Test
    void shouldReturn404WhenInventoryItemDoesNotExistForFefoWithdrawal() throws Exception {
        var itemId = UUID.randomUUID();
        when(registerFefoWithdrawal.execute(any(RegisterFefoWithdrawalCommand.class)))
            .thenThrow(new InventoryItemNotFoundException(itemId));

        mockMvc.perform(fefoWithdrawal(itemId, "{ \"quantity\": 1 }"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("INVENTORY_ITEM_NOT_FOUND"));
    }

    @Test
    void shouldReturn422WhenInventoryItemIsInactiveForFefoWithdrawal() throws Exception {
        var itemId = UUID.randomUUID();
        when(registerFefoWithdrawal.execute(any(RegisterFefoWithdrawalCommand.class)))
            .thenThrow(new InactiveInventoryItemException(itemId));

        mockMvc.perform(fefoWithdrawal(itemId, "{ \"quantity\": 1 }"))
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.code").value("INACTIVE_INVENTORY_ITEM"));
    }

    @Test
    void shouldReturn422WithStructuredDetailsWhenEligibleStockIsInsufficient() throws Exception {
        var itemId = UUID.randomUUID();
        when(registerFefoWithdrawal.execute(any(RegisterFefoWithdrawalCommand.class)))
            .thenThrow(new InsufficientEligibleStockException(
                itemId,
                new BigDecimal("80.000"),
                new BigDecimal("55.000")
            ));

        mockMvc.perform(fefoWithdrawal(itemId, "{ \"quantity\": 80.000 }"))
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_ELIGIBLE_STOCK"))
            .andExpect(jsonPath("$.details.inventoryItemId").value(itemId.toString()))
            .andExpect(jsonPath("$.details.requestedQuantity").value("80.000"))
            .andExpect(jsonPath("$.details.availableQuantity").value("55.000"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder fefoWithdrawal(
        UUID inventoryItemId,
        String requestBody
    ) {
        return post("/api/v1/inventory/items/{inventoryItemId}/withdrawals", inventoryItemId)
            .contentType("application/json")
            .with(csrf())
            .content(requestBody);
    }

    private void assertInvalidFefoRequest(String requestBody, String field) throws Exception {
        var itemId = UUID.randomUUID();

        mockMvc.perform(fefoWithdrawal(itemId, requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.details." + field).exists());

        verify(registerFefoWithdrawal, never()).execute(any(RegisterFefoWithdrawalCommand.class));
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
