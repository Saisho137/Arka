package com.arka.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI notificationsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ms-notifications API")
                        .description("Microservicio de Notificaciones — Plataforma B2B Arka. "
                                + "Event-driven: consume eventos de Kafka y envía emails via AWS SES.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Arka Platform Team")
                                .email("platform@arka.com")));
    }
}
