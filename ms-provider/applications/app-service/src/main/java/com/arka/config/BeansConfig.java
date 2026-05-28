package com.arka.config;

import com.arka.config.serializer.JacksonJsonSerializer;
import com.arka.usecase.commons.JsonSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class BeansConfig {

    @Bean
    public JsonSerializer jsonSerializer(ObjectMapper objectMapper) {
        return new JacksonJsonSerializer(objectMapper);
    }
}
