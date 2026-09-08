package com.ceudelavanda.lavandaflow.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

    private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(
        HttpServletRequest request,
        HttpServletResponse response,
        Supplier<CsrfToken> deferredCsrfToken
    ) {
        xor.handle(request, response, deferredCsrfToken);
    }

    @Override
    public String resolveCsrfTokenValue(
        HttpServletRequest request,
        CsrfToken csrfToken
    ) {
        var handler = StringUtils.hasText(request.getHeader(csrfToken.getHeaderName()))
            ? plain
            : xor;
        return handler.resolveCsrfTokenValue(request, csrfToken);
    }
}
