package com.arka.api.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder(toBuilder = true)
public record ReviewResponse(
    UUID reviewId,
    String userId,
    int rating,
    String comment,
    Instant createdAt
) {}
