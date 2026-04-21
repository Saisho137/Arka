package com.arka.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder(toBuilder = true)
public record ChangeStatusRequest(
    @NotBlank(message = "newStatus is required")
    String newStatus
) {
}
