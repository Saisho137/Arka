package com.arka.api.dto;

import lombok.Builder;

@Builder
public record ErrorResponse(String code, String message) {}
