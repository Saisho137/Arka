package com.arka.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Builder;

@Builder(toBuilder = true)
public record UpdateStockRequest(
        @NotNull @PositiveOrZero Integer quantity,
        String reason
) {}
