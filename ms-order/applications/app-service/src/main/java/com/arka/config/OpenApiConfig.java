package com.arka.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI orderOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ms-order API")
                        .description("Microservicio de Gestión de Pedidos — Plataforma B2B Arka. " +
                                "Orquestador del Saga de 4 pasos: reserva de stock, procesamiento de pago y despacho.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Arka Platform Team")
                                .email("platform@arka.com")));
    }
}
