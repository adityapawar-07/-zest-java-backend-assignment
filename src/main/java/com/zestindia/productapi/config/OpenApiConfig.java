package com.zestindia.productapi.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger / OpenAPI 3 documentation, served at:
 *  - /swagger-ui.html      (interactive UI)
 *  - /v3/api-docs          (raw JSON spec)
 *
 * Registers a "bearerAuth" scheme so requests can be authorized in the UI
 * with the accessToken returned from POST /api/v1/auth/login - click
 * "Authorize", paste the token (no "Bearer " prefix needed), and every
 * protected endpoint you try from the UI will send it automatically.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI productApiOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Product API")
                        .description("RESTful CRUD API for Products (and their Items) - "
                                + "Zest India Java Backend Developer assignment. "
                                + "JWT access tokens are required for every /api/v1/products/** "
                                + "endpoint; obtain one via /api/v1/auth/login.")
                        .version("v1.0.0")
                        .contact(new Contact().name("Zest India IT Services - Java Backend Assignment")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME_NAME, new SecurityScheme()
                                .name(BEARER_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
