package com.example.bankingtransactionservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI document metadata and the bearer-token security scheme. */
@Configuration
public class OpenApiConfig {

    /** Describes the API and registers the JWT bearer scheme referenced by the controllers. */
    @Bean
    public OpenAPI bankingOpenApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Banking Transaction Service API")
                                .version("1.0.0")
                                .description(
                                        "Enterprise banking transaction service. Authenticate at "
                                                + "POST /api/auth/login to obtain a bearer token, then send it as "
                                                + "'Authorization: Bearer <token>' on every other endpoint.\n\n"
                                                + "Roles: ADMIN (full access including the audit trail), "
                                                + "TELLER (operates on any customer account), "
                                                + "CUSTOMER (restricted to their own accounts).")
                                .contact(new Contact().name("Platform Engineering"))
                                .license(new License().name("Proprietary")))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        "bearerAuth",
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")
                                                .description("JWT issued by POST /api/auth/login")));
    }
}
