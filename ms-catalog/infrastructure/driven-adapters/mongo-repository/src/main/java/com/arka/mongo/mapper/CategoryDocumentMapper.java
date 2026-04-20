package com.arka.mongo.mapper;

import com.arka.model.category.Category;
import com.arka.mongo.document.CategoryDocument;

/**
 * Static mapper for converting between Category domain model and CategoryDocument.
 */
public final class CategoryDocumentMapper {
    
    private CategoryDocumentMapper() {
        throw new UnsupportedOperationException("Utility class");
    }
    
    /**
     * Converts Category domain model to CategoryDocument for MongoDB persistence.
     *
     * @param category the domain model
     * @return the MongoDB document
     */
    public static CategoryDocument toDocument(Category category) {
        if (category == null) {
            return null;
        }
        
        return CategoryDocument.builder()
                .id(category.id())
                .name(category.name())
                .description(category.description())
                .active(category.active())
                .createdAt(category.createdAt())
                .build();
    }
    
    /**
     * Converts CategoryDocument from MongoDB to Category domain model.
     *
     * @param document the MongoDB document
     * @return the domain model
     */
    public static Category toDomain(CategoryDocument document) {
        if (document == null) {
            return null;
        }
        
        return Category.builder()
                .id(document.getId())
                .name(document.getName())
                .description(document.getDescription())
                .active(document.isActive())
                .createdAt(document.getCreatedAt())
                .build();
    }
}
