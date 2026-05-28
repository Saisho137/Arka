package com.arka.helper;

import com.arka.model.payment.gateways.JsonSerializer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class JacksonJsonSerializer implements JsonSerializer {

    private final ObjectMapper objectMapper;

    @Override
    public String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize payload: " + e.getMessage(), e);
        }
    }
}
