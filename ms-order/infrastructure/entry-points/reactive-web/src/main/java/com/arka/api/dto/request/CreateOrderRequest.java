package com.arka.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder(toBuilder = true)
public record CreateOrderRequest(
    @NotNull(message = "customerId is required")
    UUID customerId,

    @NotBlank(message = "customerEmail is required")
    String customerEmail,

    @NotBlank(message = "shippingAddress is required")
    String shippingAddress,

    @NotEmpty(message = "items list cannot be empty")
    List<@Valid OrderItemRequest> items,

    String notes
) {
}
