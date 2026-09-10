package com.ceudelavanda.lavandaflow.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/** Serves current Angular client routes through the packaged SPA entry document. */
@Configuration(proxyBeanMethods = false)
class SpaWebConfiguration implements WebMvcConfigurer {

    private static final List<String> SPA_ROUTES = List.of(
        "/",
        "/login",
        "/dashboard",
        "/catalog/**",
        "/inventory/**",
        "/suppliers/**",
        "/receipts/**",
        "/production/**"
    );

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        SPA_ROUTES.forEach(route ->
            registry.addViewController(route).setViewName("forward:/index.html")
        );
    }
}
