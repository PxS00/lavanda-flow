package com.ceudelavanda.lavandaflow.suppliers.infrastructure.web;

import com.ceudelavanda.lavandaflow.shared.config.ClockConfig;
import com.ceudelavanda.lavandaflow.suppliers.application.GetSupplier;
import com.ceudelavanda.lavandaflow.suppliers.application.RegisterSupplier;
import com.ceudelavanda.lavandaflow.suppliers.application.RegisterSupplierCommand;
import com.ceudelavanda.lavandaflow.suppliers.application.SearchSuppliers;
import com.ceudelavanda.lavandaflow.suppliers.application.SupplierNotFoundException;
import com.ceudelavanda.lavandaflow.suppliers.application.SupplierPage;
import com.ceudelavanda.lavandaflow.suppliers.application.SupplierResult;
import com.ceudelavanda.lavandaflow.suppliers.application.SupplierSearchQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SupplierController.class)
@Import(ClockConfig.class)
@WithMockUser
class SupplierControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private RegisterSupplier registerSupplier;
    @MockitoBean private GetSupplier getSupplier;
    @MockitoBean private SearchSuppliers searchSuppliers;

    @Test
    void shouldRegisterSupplier() throws Exception {
        var supplierId = UUID.randomUUID();
        var command = new RegisterSupplierCommand("Issue94 Aromas", "ID-94", "contact@example.com", "Notes");
        when(registerSupplier.execute(command)).thenReturn(new SupplierResult(
            supplierId, "Issue94 Aromas", "ID-94", "contact@example.com", "Notes", true
        ));

        mockMvc.perform(post("/api/v1/suppliers")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Issue94 Aromas",
                      "identifier": "ID-94",
                      "contact": "contact@example.com",
                      "notes": "Notes"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/v1/suppliers/" + supplierId))
            .andExpect(jsonPath("$.id").value(supplierId.toString()))
            .andExpect(jsonPath("$.name").value("Issue94 Aromas"))
            .andExpect(jsonPath("$.identifier").value("ID-94"))
            .andExpect(jsonPath("$.active").value(true));

        verify(registerSupplier).execute(command);
    }

    @Test
    void shouldReturnValidationErrorForInvalidRegistration() throws Exception {
        mockMvc.perform(post("/api/v1/suppliers")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": " ",
                      "identifier": "ID-94"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.details.name").exists());
    }

    @Test
    void shouldRetrieveSupplier() throws Exception {
        var supplierId = UUID.randomUUID();
        when(getSupplier.execute(supplierId)).thenReturn(new SupplierResult(
            supplierId, "Issue94 Aromas", null, null, null, true
        ));

        mockMvc.perform(get("/api/v1/suppliers/{supplierId}", supplierId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(supplierId.toString()))
            .andExpect(jsonPath("$.name").value("Issue94 Aromas"));
    }

    @Test
    void shouldReturnStandardizedNotFoundError() throws Exception {
        var supplierId = UUID.randomUUID();
        when(getSupplier.execute(supplierId)).thenThrow(new SupplierNotFoundException(supplierId));

        mockMvc.perform(get("/api/v1/suppliers/{supplierId}", supplierId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("SUPPLIER_NOT_FOUND"))
            .andExpect(jsonPath("$.details.supplierId").value(supplierId.toString()));
    }

    @Test
    void shouldExposeFilteredPaginatedSearch() throws Exception {
        var supplierId = UUID.randomUUID();
        var query = new SupplierSearchQuery("aromas", true, 1, 5);
        when(searchSuppliers.execute(query)).thenReturn(new SupplierPage(
            List.of(new SupplierResult(supplierId, "Issue94 Aromas", null, null, null, true)),
            1, 5, 6, 2
        ));

        mockMvc.perform(get("/api/v1/suppliers")
                .param("name", "aromas")
                .param("active", "true")
                .param("page", "1")
                .param("size", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.size").value(5))
            .andExpect(jsonPath("$.totalElements").value(6))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.content[0].id").value(supplierId.toString()));

        verify(searchSuppliers).execute(query);
    }

    @Test
    void shouldRejectInvalidPaginationAndBinding() throws Exception {
        mockMvc.perform(get("/api/v1/suppliers").param("size", "101"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_SUPPLIER_SEARCH_QUERY"));

        mockMvc.perform(get("/api/v1/suppliers").param("active", "not-a-boolean"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST_PARAMETER"));
    }
}
