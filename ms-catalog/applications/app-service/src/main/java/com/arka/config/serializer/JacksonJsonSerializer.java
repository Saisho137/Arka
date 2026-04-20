package com.arka.config.serializer;

import com.arka.model.commons.gateways.JsonSerializer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Jackson-backed implementation of the domain port {@link JsonSerializer}.
 * Lives in app-service (the assembly point) because it bridges the domain port
 * with the Jackson infrastructure provided by Spring Boot auto-configuration.
 */
@Component
@RequiredArgsConstructor
public class JacksonJsonSerializer implements JsonSerializer {

    private final ObjectMapper objectMapper;

    @Override
    public String toJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize object to JSON: " + e.getMessage(), e);
        }
    }
}
