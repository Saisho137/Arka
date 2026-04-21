package com.arka.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;

@Builder(toBuilder = true)
public record OrderItemRequest(
    @NotBlank(message = "sku is required")
    String sku,

    @NotNull(message = "quantity is required")
    @Positive(message = "quantity must be positive")
    Integer quantity
) {
}
