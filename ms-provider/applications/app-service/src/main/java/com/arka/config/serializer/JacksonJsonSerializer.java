package com.arka.config.serializer;

import com.arka.usecase.commons.JsonSerializer;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

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
