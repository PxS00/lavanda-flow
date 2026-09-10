package com.ceudelavanda.lavandaflow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ManagementConfigurationTest {

    @Autowired
    private Environment environment;

    @Test
    void exposesOnlyApprovedManagementEndpoints() {
        assertEquals(
                "health",
                environment.getProperty("management.endpoints.web.exposure.include"));
        assertEquals(
                "never",
                environment.getProperty("management.endpoint.health.show-details"));
        assertEquals("false", environment.getProperty("springdoc.api-docs.enabled"));
        assertEquals("false", environment.getProperty("springdoc.swagger-ui.enabled"));
        assertEquals("12h", environment.getProperty("server.servlet.session.timeout"));
        assertEquals("true", environment.getProperty("server.servlet.session.cookie.http-only"));
        assertEquals("lax", environment.getProperty("server.servlet.session.cookie.same-site"));
        assertEquals("/", environment.getProperty("server.servlet.session.cookie.path"));
        assertEquals("false", environment.getProperty("server.servlet.session.cookie.secure"));
    }
}
