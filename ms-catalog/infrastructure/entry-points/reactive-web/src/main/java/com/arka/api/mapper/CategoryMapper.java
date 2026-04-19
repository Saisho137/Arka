package com.arka.api.mapper;

import com.arka.api.dto.CategoryResponse;
import com.arka.model.category.Category;

public final class CategoryMapper {

    private CategoryMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static CategoryResponse toResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.id())
                .name(category.name())
                .description(category.description())
                .active(category.active())
                .createdAt(category.createdAt())
                .build();
    }
}
