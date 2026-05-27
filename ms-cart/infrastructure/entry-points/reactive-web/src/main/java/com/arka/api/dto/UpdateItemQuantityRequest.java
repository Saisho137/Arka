package com.arka.api.dto;

import jakarta.validation.constraints.Positive;
import lombok.Builder;

@Builder
public record UpdateItemQuantityRequest(@Positive int quantity) {}
