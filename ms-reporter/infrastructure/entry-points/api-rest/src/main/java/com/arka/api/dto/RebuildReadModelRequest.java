package com.arka.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder(toBuilder = true)
public record RebuildReadModelRequest(
        @NotBlank String readModel
) {
}
