package com.ceudelavanda.lavandaflow.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
class AuthenticationController {

    private final AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final SecurityErrorWriter errorWriter;
    private final LogoutHandler logoutHandler;

    @PostMapping("/login")
    ResponseEntity<?> login(
        @Valid @RequestBody LoginRequest body,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                    body.username().trim(),
                    body.password()
                )
            );
        } catch (AuthenticationException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                errorWriter.response(
                    HttpStatus.UNAUTHORIZED,
                    "AUTHENTICATION_FAILED",
                    "Authentication failed",
                    request
                )
            );
        }

        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        return ResponseEntity.ok(new SessionResponse(true, authentication.getName()));
    }

    @GetMapping("/session")
    SessionResponse session(Authentication authentication, CsrfToken csrfToken) {
        csrfToken.getToken();
        if (authentication == null || !authentication.isAuthenticated()) {
            return new SessionResponse(false, null);
        }
        return new SessionResponse(true, authentication.getName());
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(
        Authentication authentication,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        logoutHandler.logout(request, response, authentication);
        return ResponseEntity.noContent().build();
    }

    record LoginRequest(
        @NotBlank @Size(max = 100) String username,
        @NotBlank String password
    ) {
    }

    record SessionResponse(boolean authenticated, String username) {
    }
}
