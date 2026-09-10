package com.ceudelavanda.lavandaflow.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OperatorBootstrapTest {

    private final OperatorAccountRepository repository = mock(OperatorAccountRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    @Test
    void disabledBootstrapDoesNothing() {
        bootstrap(new OperatorBootstrapProperties(false, null, null)).run(arguments());

        verifyNoInteractions(repository, passwordEncoder);
    }

    @Test
    void enabledBootstrapCreatesOneHashedAccount() {
        when(repository.findByUsernameIgnoreCase("Operator")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("external-secret")).thenReturn("{bcrypt}encoded");

        bootstrap(new OperatorBootstrapProperties(
            true, "  Operator  ", "external-secret"
        )).run(arguments());

        verify(passwordEncoder).encode("external-secret");
        verify(repository).saveAndFlush(any(OperatorAccount.class));
    }

    @Test
    void rerunWithDifferentCaseLeavesExistingPasswordUntouched() {
        var existing = new OperatorAccount(
            java.util.UUID.randomUUID(), "Operator", "{bcrypt}existing"
        );
        when(repository.findByUsernameIgnoreCase("operator"))
            .thenReturn(Optional.of(existing));

        bootstrap(new OperatorBootstrapProperties(
            true, " operator ", "replacement-secret"
        )).run(arguments());

        verify(passwordEncoder, never()).encode(any());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void enabledBootstrapRejectsMissingCredentials() {
        assertThatThrownBy(() -> bootstrap(new OperatorBootstrapProperties(
            true, " ", "secret"
        )).run(arguments()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Bootstrap username is required when operator bootstrap is enabled");

        assertThatThrownBy(() -> bootstrap(new OperatorBootstrapProperties(
            true, "operator", " "
        )).run(arguments()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Bootstrap password is required when operator bootstrap is enabled");
    }

    private OperatorBootstrap bootstrap(OperatorBootstrapProperties properties) {
        return new OperatorBootstrap(properties, repository, passwordEncoder);
    }

    private static DefaultApplicationArguments arguments() {
        return new DefaultApplicationArguments();
    }
}
