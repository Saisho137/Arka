package com.arka.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI inventoryOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ms-inventory API")
                        .description("Microservicio de Gestión de Stock y Reservas — Plataforma B2B Arka. " +
                                "Previene sobreventas mediante lock pesimista (SELECT FOR UPDATE) en PostgreSQL 17.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Arka Platform Team")
                                .email("platform@arka.com")));
    }
}
