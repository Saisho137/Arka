package com.arka.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record CreateCartRequest(@NotBlank String customerId) {}
