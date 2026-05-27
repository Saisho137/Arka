package com.arka.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Builder;

@Builder
public record AddItemRequest(@NotBlank String sku, @Positive int quantity) {}
