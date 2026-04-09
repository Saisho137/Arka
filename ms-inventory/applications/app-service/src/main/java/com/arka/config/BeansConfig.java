package com.arka.config;

import com.arka.config.serializer.JacksonJsonSerializer;
import com.arka.usecase.stock.JsonSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * Wires cross-cutting infrastructure beans that don't belong to a specific module.
 *
 * <p>Beans declared here:
 * <ul>
 *   <li>{@link JsonSerializer} — Jackson-backed serializer used by domain UseCases
 *       to serialize event payloads before storing them in the outbox table.</li>
 * </ul>
 */
@Configuration
public class BeansConfig {

    @Bean
    public JsonSerializer jsonSerializer(ObjectMapper objectMapper) {
        return new JacksonJsonSerializer(objectMapper);
    }
}
