package com.arka.model.category;

import lombok.Builder;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Category aggregate root representing a product category.
 * Categories are master data used to organize products in the catalog.
 */
@Builder(toBuilder = true)
public record Category(
    UUID id,
    String name,
    String description,
    boolean active,
    Instant createdAt
) {
    public Category {
        Objects.requireNonNull(name, "Name is required");
    }

    public Category deactivate() {
        if (!this.active) {
            throw new IllegalStateException("Category is already inactive");
        }
        return this.toBuilder()
                   .active(false)
                   .build();
    }
    
    // Lógica de Dominio: Reactivar una categoría
    public Category activate() {
        if (this.active) {
            throw new IllegalStateException("Category is already active");
        }
        return this.toBuilder()
                   .active(true)
                   .build();
    }
    
    // Lógica de Dominio: Actualizar información básica
    public Category updateDetails(String newName, String newDescription) {
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("New name cannot be empty");
        }
        return this.toBuilder()
                   .name(newName)
                   .description(newDescription)
                   .build();
    }
}
