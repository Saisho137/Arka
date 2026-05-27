package com.arka.scheduler.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "cart.abandonment")
public record AbandonmentConfig(
        @NotNull Duration checkInterval,
        @NotNull Duration threshold
) {}
