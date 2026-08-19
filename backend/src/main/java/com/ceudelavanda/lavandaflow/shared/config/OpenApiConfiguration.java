package com.ceudelavanda.lavandaflow.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Defines stable metadata for the Lavanda Flow HTTP API contract.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    @Bean
    OpenAPI lavandaFlowOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Lavanda Flow API")
                        .description("Inventory and production management API for Céu de Lavanda.")
                        .version("v1"));
    }
}
