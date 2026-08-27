package com.ceudelavanda.lavandaflow.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationConfigurationTest {

    private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    @Test
    void shouldKeepBaseConfigurationEnvironmentNeutral() throws IOException {
        var propertySources = loader.load(
            "application",
            new ClassPathResource("application.yml")
        );

        assertThat(propertySources).allSatisfy(propertySource -> {
            assertThat(propertySource.getProperty("spring.profiles.default")).isNull();
            assertThat(propertySource.getProperty("spring.docker.compose.enabled")).isNull();
        });
    }

    @Test
    void shouldKeepDockerComposeConfigurationInsideLocalProfile() throws IOException {
        var propertySources = loader.load(
            "application-local",
            new ClassPathResource("application-local.yml")
        );

        assertThat(propertySources).anySatisfy(propertySource ->
            assertThat(propertySource.getProperty("spring.docker.compose.enabled"))
                .isEqualTo(true)
        );
    }
}
