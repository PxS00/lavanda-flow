package com.ceudelavanda.lavandaflow.shared.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.CompositeLogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfLogoutHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;

import java.util.List;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OperatorBootstrapProperties.class)
class SecurityConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService operatorUserDetailsService(OperatorAccountRepository repository) {
        return submittedUsername -> {
            var username = submittedUsername == null ? "" : submittedUsername.trim();
            var account = repository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("Operator not found"));
            return User.withUsername(account.username())
                .password(account.passwordHash())
                .authorities("OPERATOR")
                .build();
        };
    }

    @Bean
    AuthenticationManager authenticationManager(
        UserDetailsService userDetailsService,
        PasswordEncoder passwordEncoder
    ) {
        var provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        var repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/");
        return repository;
    }

    @Bean
    SpaCsrfTokenRequestHandler spaCsrfTokenRequestHandler() {
        return new SpaCsrfTokenRequestHandler();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(
        CsrfTokenRepository csrfTokenRepository
    ) {
        return new CompositeSessionAuthenticationStrategy(List.of(
            new ChangeSessionIdAuthenticationStrategy(),
            new CsrfAuthenticationStrategy(csrfTokenRepository)
        ));
    }

    @Bean
    LogoutHandler logoutHandler(
        SecurityContextRepository securityContextRepository,
        CsrfTokenRepository csrfTokenRepository
    ) {
        var securityContext = new SecurityContextLogoutHandler();
        securityContext.setSecurityContextRepository(securityContextRepository);
        return new CompositeLogoutHandler(
            securityContext,
            new CsrfLogoutHandler(csrfTokenRepository)
        );
    }

    @Bean
    SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        SecurityErrorWriter errorWriter,
        CsrfTokenRepository csrfTokenRepository,
        SpaCsrfTokenRequestHandler csrfTokenRequestHandler,
        SecurityContextRepository securityContextRepository,
        SessionAuthenticationStrategy sessionAuthenticationStrategy
    ) throws Exception {
        return http
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfTokenRepository)
                .csrfTokenRequestHandler(csrfTokenRequestHandler)
            )
            .securityContext(context -> context
                .securityContextRepository(securityContextRepository)
                .requireExplicitSave(true)
            )
            .sessionManagement(session -> session
                .sessionAuthenticationStrategy(sessionAuthenticationStrategy)
            )
            .logout(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/auth/session",
                    "/actuator/health",
                    "/actuator/info",
                    "/actuator/prometheus"
                ).permitAll()
                .requestMatchers("/api/v1/**").authenticated()
                .requestMatchers("/actuator/**").denyAll()
                .anyRequest().permitAll()
            )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, exception) ->
                    errorWriter.write(
                        request,
                        response,
                        HttpStatus.UNAUTHORIZED,
                        "AUTHENTICATION_REQUIRED",
                        "Authentication is required"
                    )
                )
                .accessDeniedHandler((request, response, exception) -> {
                    var csrfFailure = exception instanceof CsrfException;
                    errorWriter.write(
                        request,
                        response,
                        HttpStatus.FORBIDDEN,
                        csrfFailure ? "CSRF_VALIDATION_FAILED" : "ACCESS_DENIED",
                        csrfFailure ? "CSRF token is missing or invalid" : "Access is denied"
                    );
                })
            )
            .build();
    }
}
