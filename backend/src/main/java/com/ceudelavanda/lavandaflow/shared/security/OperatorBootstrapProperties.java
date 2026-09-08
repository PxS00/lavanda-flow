package com.ceudelavanda.lavandaflow.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lavanda.security.bootstrap")
record OperatorBootstrapProperties(
    boolean enabled,
    String username,
    String password
) {
}
