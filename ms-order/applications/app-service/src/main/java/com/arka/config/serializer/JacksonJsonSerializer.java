package com.arka.config.serializer;

import com.arka.usecase.order.JsonSerializer;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Jackson-backed implementation of the domain port {@link JsonSerializer}.
 * Lives in app-service (the assembly point) because it bridges the domain port
 * with the Jackson infrastructure provided by Spring Boot auto-configuration.
 */
@RequiredArgsConstructor
public class JacksonJsonSerializer implements JsonSerializer {

    private final ObjectMapper objectMapper;

    @Override
    public String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize payload to JSON: " + e.getMessage(), e);
        }
    }
}
