package com.arka.model.commons.exception;

import java.util.UUID;

/**
 * Exception thrown when a category is not found by ID or name.
 * HTTP Status: 400 Bad Request
 */
public class CategoryNotFoundException extends DomainException {

    private static final String ERROR_CODE = "CATEGORY_NOT_FOUND";
    private static final int HTTP_STATUS = 400;

    public CategoryNotFoundException(String message) {
        super(message);
    }

    public CategoryNotFoundException(UUID categoryId) {
        super(String.format("Category not found with ID: %s", categoryId));
    }

    public CategoryNotFoundException(String field, String value) {
        super(String.format("Category not found with %s: %s", field, value));
    }

    @Override
    public int getHttpStatus() {
        return HTTP_STATUS;
    }

    @Override
    public String getCode() {
        return ERROR_CODE;
    }
}
