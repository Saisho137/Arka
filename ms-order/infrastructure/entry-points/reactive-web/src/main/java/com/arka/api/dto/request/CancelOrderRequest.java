package com.arka.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder(toBuilder = true)
public record CancelOrderRequest(
    @NotBlank(message = "reason is required")
    String reason
) {
}
