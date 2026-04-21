package com.arka.api.dto.response;

public record ErrorResponse(
    String code,
    String message
) {
}
