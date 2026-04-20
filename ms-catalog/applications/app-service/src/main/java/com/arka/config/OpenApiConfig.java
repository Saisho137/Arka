package com.arka.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI catalogOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ms-catalog API")
                        .description("Microservicio de Catálogo Maestro de Productos — Plataforma B2B Arka. " +
                                "Gestiona productos, categorías y reseñas con MongoDB, Redis (Cache-Aside) y " +
                                "Kafka (Outbox Pattern).")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Arka Platform Team")
                                .email("platform@arka.com")));
    }
}
