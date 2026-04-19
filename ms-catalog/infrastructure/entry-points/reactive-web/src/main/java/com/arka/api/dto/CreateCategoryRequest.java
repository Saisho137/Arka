package com.arka.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder(toBuilder = true)
public record CreateCategoryRequest(
    @NotBlank(message = "Name is required")
    String name,

    String description
) {}
