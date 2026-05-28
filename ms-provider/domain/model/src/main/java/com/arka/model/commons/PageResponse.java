package com.arka.model.commons;

import lombok.Builder;

import java.util.List;

@Builder(toBuilder = true)
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
