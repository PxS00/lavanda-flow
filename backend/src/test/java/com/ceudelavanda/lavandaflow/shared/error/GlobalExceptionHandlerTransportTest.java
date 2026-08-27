package com.ceudelavanda.lavandaflow.shared.error;

import com.ceudelavanda.lavandaflow.inventory.domain.MovementType;
import com.ceudelavanda.lavandaflow.shared.config.ClockConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransportErrorTestController.class)
@Import(ClockConfig.class)
@WithMockUser
class GlobalExceptionHandlerTransportTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldStandardizeMalformedUuid() throws Exception {
        mockMvc.perform(get("/transport/uuid/not-a-uuid"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST_PARAMETER"))
            .andExpect(jsonPath("$.message").value("Request parameter has an invalid value"))
            .andExpect(jsonPath("$.details.id").value("Invalid value"));
    }

    @Test
    void shouldStandardizeInvalidEnum() throws Exception {
        mockMvc.perform(get("/transport/query")
                .param("type", "TYPO")
                .param("from", "2026-08-27T12:00:00Z")
                .param("page", "0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST_PARAMETER"))
            .andExpect(jsonPath("$.details.type").value("Invalid value"));
    }

    @Test
    void shouldStandardizeInvalidInstant() throws Exception {
        mockMvc.perform(get("/transport/query")
                .param("type", "ENTRY")
                .param("from", "not-an-instant")
                .param("page", "0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST_PARAMETER"))
            .andExpect(jsonPath("$.details.from").value("Invalid value"));
    }

    @Test
    void shouldStandardizeInvalidNumericParameter() throws Exception {
        mockMvc.perform(get("/transport/query")
                .param("type", "ENTRY")
                .param("from", "2026-08-27T12:00:00Z")
                .param("page", "abc"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST_PARAMETER"))
            .andExpect(jsonPath("$.details.page").value("Invalid value"));
    }

    @Test
    void shouldStandardizeMissingRequiredParameter() throws Exception {
        mockMvc.perform(get("/transport/required"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("MISSING_REQUEST_PARAMETER"))
            .andExpect(jsonPath("$.details.required").value("Required parameter"));
    }

    @Test
    void shouldStandardizeMalformedJsonWithoutExposingParserDetails() throws Exception {
        mockMvc.perform(post("/transport/body")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST_BODY"))
            .andExpect(jsonPath("$.message").value("Request body is malformed or unreadable"))
            .andExpect(jsonPath("$.details").doesNotExist());
    }
}

@RestController
@RequestMapping("/transport")
class TransportErrorTestController {

    @GetMapping("/uuid/{id}")
    void uuid(@PathVariable UUID id) {
    }

    @GetMapping("/query")
    void query(
        @RequestParam MovementType type,
        @RequestParam Instant from,
        @RequestParam int page
    ) {
    }

    @GetMapping("/required")
    void required(@RequestParam String required) {
    }

    @PostMapping("/body")
    void body(@RequestBody TransportBody body) {
    }

    record TransportBody(String value) {
    }
}
