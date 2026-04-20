package com.arka.redis.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY;
import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE;

/**
 * Redis configuration for reactive cache operations.
 * ReactiveRedisTemplate<String,String> is already auto-configured by Spring Boot
 * as 'reactiveStringRedisTemplate' — no need to redefine it here.
 */
@Configuration
public class RedisConfig {

    /**
     * Configure ObjectMapper for JSON serialization of domain models.
     * Registers JavaTimeModule for Instant serialization.
     * Configures for Java records support and ignores getters/setters.
     * Named 'jacksonObjectMapper' to avoid conflict with the reactivecommons
     * ObjectMapper bean defined in ObjectMapperConfig (app-service).
     */
    @Bean
    public ObjectMapper jacksonObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // findAndRegisterModules discovers JavaTimeModule (and others) from the classpath
        mapper.findAndRegisterModules();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Only serialize fields, ignore getters/methods
        mapper.setVisibility(mapper.getSerializationConfig().getDefaultVisibilityChecker()
                .withFieldVisibility(ANY)
                .withGetterVisibility(NONE)
                .withSetterVisibility(NONE)
                .withIsGetterVisibility(NONE));
        return mapper;
    }
}
