package com.arka.api.dto;

import lombok.Builder;

import java.util.UUID;

@Builder(toBuilder = true)
public record RebuildResponse(
        UUID jobId,
        String readModel,
        String status
) {
}
