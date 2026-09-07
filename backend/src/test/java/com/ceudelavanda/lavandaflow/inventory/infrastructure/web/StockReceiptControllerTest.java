package com.ceudelavanda.lavandaflow.inventory.infrastructure.web;

import com.ceudelavanda.lavandaflow.inventory.application.receipt.RegisterStockReceipt;
import com.ceudelavanda.lavandaflow.inventory.application.receipt.RegisterStockReceiptCommand;
import com.ceudelavanda.lavandaflow.inventory.application.receipt.StockReceiptResult;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InactiveSupplierException;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InvalidBatchDataException;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.InventoryItemNotFoundException;
import com.ceudelavanda.lavandaflow.inventory.domain.exception.SupplierNotFoundException;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StockReceiptController.class)
@Import({ClockConfig.class, ExactDecimalJsonConfiguration.class})
@WithMockUser
class StockReceiptControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private RegisterStockReceipt registerStockReceipt;

    @Test
    void shouldReturnCreatedReceipt() throws Exception {
        var itemId = UUID.randomUUID();
        var supplierId = UUID.randomUUID();
        var batchId = UUID.randomUUID();
        var movementId = UUID.randomUUID();
        when(registerStockReceipt.execute(any())).thenReturn(new StockReceiptResult(
            batchId,
            movementId,
            itemId,
            supplierId,
            "LOT-96",
            new BigDecimal("25.500000"),
            LocalDate.of(2026, 8, 31),
            LocalDate.of(2027, 8, 31),
            "Purchase receipt",
            Instant.parse("2026-08-31T15:00:00Z")
        ));

        mockMvc.perform(post("/api/v1/inventory/receipts")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest(itemId, supplierId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.batchId").value(batchId.toString()))
            .andExpect(jsonPath("$.movementId").value(movementId.toString()))
            .andExpect(jsonPath("$.inventoryItemId").value(itemId.toString()))
            .andExpect(jsonPath("$.supplierId").value(supplierId.toString()))
            .andExpect(jsonPath("$.lotCode").value("LOT-96"))
            .andExpect(jsonPath("$.quantity").value("25.500000"))
            .andExpect(jsonPath("$.receivedAt").value("2026-08-31"))
            .andExpect(jsonPath("$.expiresAt").value("2027-08-31"))
            .andExpect(jsonPath("$.reason").value("Purchase receipt"));
    }

    @Test
    void shouldBindQuotedExactQuantityWithoutScientificNotation() throws Exception {
        var itemId = UUID.randomUUID();
        when(registerStockReceipt.execute(any())).thenReturn(
            receiptResult(itemId, new BigDecimal("9999999999999.123456"))
        );

        mockMvc.perform(post("/api/v1/inventory/receipts")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "inventoryItemId": "%s",
                      "lotCode": "LOT-96",
                      "quantity": "9999999999999.123456",
                      "receivedAt": "2026-08-31"
                    }
                    """.formatted(itemId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.quantity").value("9999999999999.123456"));

        var captor = org.mockito.ArgumentCaptor.forClass(RegisterStockReceiptCommand.class);
        verify(registerStockReceipt).execute(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().quantity())
            .isEqualByComparingTo("9999999999999.123456");
    }

    @Test
    void shouldKeepAcceptingLegacyNumericQuantityRequests() throws Exception {
        var itemId = UUID.randomUUID();
        when(registerStockReceipt.execute(any())).thenReturn(receiptResult(itemId, BigDecimal.ONE));

        mockMvc.perform(post("/api/v1/inventory/receipts")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest(itemId, null)))
            .andExpect(status().isCreated());

        var captor = org.mockito.ArgumentCaptor.forClass(RegisterStockReceiptCommand.class);
        verify(registerStockReceipt).execute(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().quantity()).isEqualByComparingTo("25.500000");
    }

    @Test
    void shouldReturnValidationErrorForInvalidQuantityPrecision() throws Exception {
        var itemId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/inventory/receipts")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "inventoryItemId": "%s",
                      "lotCode": "LOT-96",
                      "quantity": 1.1234567,
                      "receivedAt": "2026-08-31"
                    }
                    """.formatted(itemId)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.details.quantity").exists());
    }

    @Test
    void shouldReturnNotFoundForUnknownInventoryItem() throws Exception {
        var itemId = UUID.randomUUID();
        when(registerStockReceipt.execute(any())).thenThrow(new InventoryItemNotFoundException(itemId));

        mockMvc.perform(post("/api/v1/inventory/receipts")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest(itemId, null)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("INVENTORY_ITEM_NOT_FOUND"));
    }

    @Test
    void shouldReturnNotFoundForUnknownSupplier() throws Exception {
        var itemId = UUID.randomUUID();
        var supplierId = UUID.randomUUID();
        when(registerStockReceipt.execute(any())).thenThrow(new SupplierNotFoundException(supplierId));

        mockMvc.perform(post("/api/v1/inventory/receipts")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest(itemId, supplierId)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("SUPPLIER_NOT_FOUND"))
            .andExpect(jsonPath("$.details.supplierId").value(supplierId.toString()));
    }

    @Test
    void shouldReturnUnprocessableForInactiveSupplier() throws Exception {
        var itemId = UUID.randomUUID();
        var supplierId = UUID.randomUUID();
        when(registerStockReceipt.execute(any())).thenThrow(new InactiveSupplierException(supplierId));

        mockMvc.perform(post("/api/v1/inventory/receipts")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest(itemId, supplierId)))
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.code").value("INACTIVE_SUPPLIER"));
    }

    @Test
    void shouldReturnBadRequestForDomainBatchInvariantFailure() throws Exception {
        var itemId = UUID.randomUUID();
        when(registerStockReceipt.execute(any())).thenThrow(new InvalidBatchDataException(
            "expiresAt",
            "expiresAt must not be before receivedAt"
        ));

        mockMvc.perform(post("/api/v1/inventory/receipts")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest(itemId, null)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_BATCH_DATA"))
            .andExpect(jsonPath("$.details.expiresAt").value("expiresAt must not be before receivedAt"));
    }

    private static String validRequest(UUID itemId, UUID supplierId) {
        var supplierProperty = supplierId == null ? "" : "\"supplierId\": \"" + supplierId + "\",";
        return """
            {
              "inventoryItemId": "%s",
              %s
              "lotCode": "LOT-96",
              "quantity": 25.500000,
              "receivedAt": "2026-08-31",
              "expiresAt": "2027-08-31",
              "reason": "Purchase receipt"
            }
            """.formatted(itemId, supplierProperty);
    }

    private static StockReceiptResult receiptResult(UUID itemId, BigDecimal quantity) {
        return new StockReceiptResult(
            UUID.randomUUID(), UUID.randomUUID(), itemId, null, "LOT-96", quantity,
            LocalDate.of(2026, 8, 31), null, null, Instant.parse("2026-08-31T15:00:00Z")
        );
    }
}
