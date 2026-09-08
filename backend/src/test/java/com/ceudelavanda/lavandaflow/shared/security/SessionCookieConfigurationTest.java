package com.ceudelavanda.lavandaflow.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.server.Cookie;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SessionCookieConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(TestConfiguration.class)
        .withPropertyValues(
            "server.servlet.session.timeout=12h",
            "server.servlet.session.cookie.http-only=true",
            "server.servlet.session.cookie.same-site=lax",
            "server.servlet.session.cookie.path=/"
        );

    @Test
    void bindsTrustedLanHttpSessionContract() {
        contextRunner
            .withPropertyValues("server.servlet.session.cookie.secure=false")
            .run(context -> {
                var session = context.getBean(ServerProperties.class)
                    .getServlet().getSession();
                var cookie = session.getCookie();

                assertThat(session.getTimeout()).isEqualTo(Duration.ofHours(12));
                assertThat(cookie.getHttpOnly()).isTrue();
                assertThat(cookie.getSameSite()).isEqualTo(Cookie.SameSite.LAX);
                assertThat(cookie.getPath()).isEqualTo("/");
                assertThat(cookie.getSecure()).isFalse();
            });
    }

    @Test
    void allowsHttpsProfileToEnableSecureCookieWithoutCodeChanges() {
        contextRunner
            .withPropertyValues("server.servlet.session.cookie.secure=true")
            .run(context -> assertThat(context.getBean(ServerProperties.class)
                .getServlet().getSession().getCookie().getSecure()).isTrue());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ServerProperties.class)
    static class TestConfiguration {
    }
}
