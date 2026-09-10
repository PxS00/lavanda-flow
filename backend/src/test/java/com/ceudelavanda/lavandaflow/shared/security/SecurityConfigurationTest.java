package com.ceudelavanda.lavandaflow.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.SecurityFilterChain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SecurityConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(SecurityConfiguration.class, TestConfiguration.class);

    @Test
    void doesNotRequireServletSecurityForNonWebProcesses() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(SecurityFilterChain.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfiguration {

        @Bean
        OperatorAccountRepository operatorAccountRepository() {
            return mock(OperatorAccountRepository.class);
        }
    }
}
