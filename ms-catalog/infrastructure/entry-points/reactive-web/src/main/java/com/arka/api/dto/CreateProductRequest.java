package com.arka.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;

import java.math.BigDecimal;

@Builder(toBuilder = true)
public record CreateProductRequest(
    @NotBlank(message = "SKU is required")
    String sku,

    @NotBlank(message = "Name is required")
    String name,

    String description,

    @NotNull(message = "Cost is required")
    @Positive(message = "Cost must be positive")
    BigDecimal cost,

    @NotNull(message = "Price is required")
    @Positive(message = "Price must be positive")
    BigDecimal price,

    @NotBlank(message = "Currency is required")
    String currency,

    @NotBlank(message = "Category ID is required")
    String categoryId,

    @NotNull(message = "Initial stock is required")
    @Positive(message = "Initial stock must be positive")
    Integer initialStock
) {}
