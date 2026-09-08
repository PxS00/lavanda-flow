package com.ceudelavanda.lavandaflow.shared.security;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
class OperatorBootstrap implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(OperatorBootstrap.class);

    private final OperatorBootstrapProperties properties;
    private final OperatorAccountRepository repository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments arguments) {
        if (!properties.enabled()) {
            return;
        }

        var username = normalizeUsername(properties.username());
        var password = properties.password();
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                "Bootstrap password is required when operator bootstrap is enabled"
            );
        }

        if (repository.findByUsernameIgnoreCase(username).isPresent()) {
            LOGGER.info("Operator bootstrap skipped: account already exists");
            return;
        }

        repository.saveAndFlush(new OperatorAccount(
            UUID.randomUUID(),
            username,
            passwordEncoder.encode(password)
        ));
        LOGGER.info("Operator bootstrap created the initial account");
    }

    private static String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalStateException(
                "Bootstrap username is required when operator bootstrap is enabled"
            );
        }

        var normalized = username.trim();
        if (normalized.length() > 100) {
            throw new IllegalStateException(
                "Bootstrap username must contain at most 100 characters"
            );
        }
        return normalized;
    }
}
