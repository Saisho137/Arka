package com.arka.api.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder(toBuilder = true)
public record CategoryResponse(
    UUID id,
    String name,
    String description,
    boolean active,
    Instant createdAt
) {}
