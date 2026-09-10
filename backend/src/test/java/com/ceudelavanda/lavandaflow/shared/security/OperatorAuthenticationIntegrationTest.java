package com.ceudelavanda.lavandaflow.shared.security;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest(properties = {
    "lavanda.security.bootstrap.enabled=true",
    "lavanda.security.bootstrap.username=Operator",
    "lavanda.security.bootstrap.password=external-secret"
})
class OperatorAuthenticationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private OperatorBootstrap operatorBootstrap;

    @Test
    void bootstrapPersistsOnlyAnAdaptivePasswordHashAndDoesNotOverwriteIt() {
        var hash = jdbcTemplate.queryForObject(
            "SELECT password_hash FROM operator_account WHERE lower(username) = lower(?)",
            String.class,
            "operator"
        );

        assertThat(hash).isNotEqualTo("external-secret").startsWith("{");
        assertThat(passwordEncoder.matches("external-secret", hash)).isTrue();

        operatorBootstrap.run(new org.springframework.boot.DefaultApplicationArguments());

        assertThat(jdbcTemplate.queryForObject(
            "SELECT password_hash FROM operator_account WHERE lower(username) = lower(?)",
            String.class,
            "OPERATOR"
        )).isEqualTo(hash);
    }

    @Test
    void databaseEnforcesCaseInsensitiveUsernameUniqueness() {
        var existingHash = jdbcTemplate.queryForObject(
            "SELECT password_hash FROM operator_account WHERE lower(username) = lower(?)",
            String.class,
            "operator"
        );
        assertThatThrownBy(() -> jdbcTemplate.update(
            "INSERT INTO operator_account (id, username, password_hash) VALUES (?, ?, ?)",
            UUID.randomUUID(),
            "operator",
            existingHash
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sessionBootstrapIsPublicAndMaterializesAngularXsrfCookie() throws Exception {
        mockMvc.perform(get("/api/v1/auth/session"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(false))
            .andExpect(jsonPath("$.username").value((Object) null))
            .andExpect(result -> assertThat(xsrfCookie(result)).isNotNull());
    }

    @Test
    void validLoginRotatesSessionAndPersistsCanonicalAuthentication() throws Exception {
        var preAuthenticationSession = new MockHttpSession();
        var originalId = preAuthenticationSession.getId();
        var bootstrap = mockMvc.perform(get("/api/v1/auth/session")
                .session(preAuthenticationSession))
            .andReturn();

        var login = login(
            " operator ",
            "external-secret",
            preAuthenticationSession,
            xsrfCookie(bootstrap)
        );

        login.andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.username").value("Operator"));
        assertThat(preAuthenticationSession.getId()).isNotEqualTo(originalId);

        mockMvc.perform(get("/api/v1/auth/session").session(preAuthenticationSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.username").value("Operator"))
            .andExpect(result -> assertThat(xsrfCookie(result)).isNotNull());
    }

    @Test
    void unknownUsernameAndWrongPasswordReturnIdenticalGenericErrors() throws Exception {
        var unknown = failedLogin("unknown", "external-secret");
        var wrongPassword = failedLogin("Operator", "wrong-secret");

        assertThat(unknown.getResponse().getContentAsString())
            .contains("\"code\":\"AUTHENTICATION_FAILED\"")
            .contains("\"message\":\"Authentication failed\"")
            .doesNotContain("unknown", "wrong-secret");
        assertThat(wrongPassword.getResponse().getStatus())
            .isEqualTo(unknown.getResponse().getStatus());
        assertThat(wrongPassword.getResponse().getContentAsString())
            .contains("\"code\":\"AUTHENTICATION_FAILED\"")
            .contains("\"message\":\"Authentication failed\"")
            .doesNotContain("Operator", "wrong-secret");
    }

    @Test
    void loginAndLogoutRequireCsrfAndLogoutInvalidatesTheSession() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials("Operator", "external-secret")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("CSRF_VALIDATION_FAILED"));

        var session = authenticatedSession();
        mockMvc.perform(post("/api/v1/auth/logout").session(session))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("CSRF_VALIDATION_FAILED"));

        var csrfBootstrap = mockMvc.perform(get("/api/v1/auth/session").session(session))
            .andReturn();
        var xsrf = xsrfCookie(csrfBootstrap);
        mockMvc.perform(post("/api/v1/auth/logout")
                .session(session)
                .cookie(xsrf)
                .header("X-XSRF-TOKEN", xsrf.getValue()))
            .andExpect(status().isNoContent());

        assertThat(session.isInvalid()).isTrue();
        mockMvc.perform(get("/api/v1/inventory-items"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(get("/api/v1/auth/session"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(false))
            .andExpect(result -> assertThat(xsrfCookie(result)).isNotNull());
    }

    @Test
    void operationalRoutesRequireAuthenticationAndBusinessWritesAlsoRequireCsrf() throws Exception {
        mockMvc.perform(get("/api/v1/inventory-items"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        var session = authenticatedSession();
        mockMvc.perform(get("/api/v1/inventory-items").session(session))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/inventory-items")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("CSRF_VALIDATION_FAILED"));

        var csrfBootstrap = mockMvc.perform(get("/api/v1/auth/session").session(session))
            .andReturn();
        var xsrf = xsrfCookie(csrfBootstrap);
        mockMvc.perform(post("/api/v1/inventory-items")
                .session(session)
                .cookie(xsrf)
                .header("X-XSRF-TOKEN", xsrf.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void defaultOperationalExposureIsMinimalAndDeniedPathsUseSafeErrors() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").exists())
            .andExpect(jsonPath("$.components").doesNotExist());
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/info"))
            .andExpect(status().isNotFound());

        mockMvc.perform(get("/actuator/env").session(authenticatedSession()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
            .andExpect(jsonPath("$.message").value("Access is denied"))
            .andExpect(jsonPath("$.details").doesNotExist());
    }

    private MockHttpSession authenticatedSession() throws Exception {
        var session = new MockHttpSession();
        var bootstrap = mockMvc.perform(get("/api/v1/auth/session").session(session))
            .andReturn();
        login("Operator", "external-secret", session, xsrfCookie(bootstrap))
            .andExpect(status().isOk());
        return session;
    }

    private org.springframework.test.web.servlet.ResultActions login(
        String username,
        String password,
        MockHttpSession session,
        Cookie xsrf
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
            .session(session)
            .cookie(xsrf)
            .header("X-XSRF-TOKEN", xsrf.getValue())
            .contentType(MediaType.APPLICATION_JSON)
            .content(credentials(username, password)));
    }

    private MvcResult failedLogin(String username, String password) throws Exception {
        var session = new MockHttpSession();
        var bootstrap = mockMvc.perform(get("/api/v1/auth/session").session(session))
            .andReturn();
        return login(username, password, session, xsrfCookie(bootstrap))
            .andExpect(status().isUnauthorized())
            .andReturn();
    }

    private static Cookie xsrfCookie(MvcResult result) {
        return result.getResponse().getCookie("XSRF-TOKEN");
    }

    private static String credentials(String username, String password) {
        return """
            {"username":"%s","password":"%s"}
            """.formatted(username, password);
    }
}
