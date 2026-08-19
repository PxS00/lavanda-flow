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
                "health,info,prometheus",
                environment.getProperty("management.endpoints.web.exposure.include"));
        assertEquals(
                "when-authorized",
                environment.getProperty("management.endpoint.health.show-details"));
    }
}
