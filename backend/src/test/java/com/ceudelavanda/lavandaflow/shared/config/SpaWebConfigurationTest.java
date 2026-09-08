package com.ceudelavanda.lavandaflow.shared.config;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest
class SpaWebConfigurationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void shouldForwardPublicSpaEntryAndClientRoutes() throws Exception {
        mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(forwardedUrl("/index.html"));
        mockMvc.perform(get("/login"))
            .andExpect(status().isOk())
            .andExpect(forwardedUrl("/index.html"));
        mockMvc.perform(get("/dashboard"))
            .andExpect(status().isOk())
            .andExpect(forwardedUrl("/index.html"));
        mockMvc.perform(get("/production/genealogy/batches/batch-id"))
            .andExpect(status().isOk())
            .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void shouldLeaveStaticResourcesAndInfrastructureRoutesUntouched() throws Exception {
        mockMvc.perform(get("/runtime-test.txt"))
            .andExpect(status().isOk())
            .andExpect(forwardedUrl(null))
            .andExpect(content().string("static resource\n"));
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(forwardedUrl(null))
            .andExpect(jsonPath("$.status").exists());
        mockMvc.perform(get("/api/v1/auth/session"))
            .andExpect(status().isOk())
            .andExpect(forwardedUrl(null))
            .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    @WithMockUser
    void shouldNotForwardUnknownApiRoutes() throws Exception {
        mockMvc.perform(get("/api/v1/runtime-missing"))
            .andExpect(status().isNotFound())
            .andExpect(forwardedUrl(null));
    }
}
