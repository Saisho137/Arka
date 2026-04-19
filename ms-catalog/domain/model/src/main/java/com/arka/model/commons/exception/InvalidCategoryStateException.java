package com.arka.model.commons.exception;

import java.util.UUID;

/**
 * Exception thrown when attempting an invalid state transition on a category.
 * HTTP Status: 409 Conflict
 */
public class InvalidCategoryStateException extends DomainException {

    private static final String ERROR_CODE = "INVALID_CATEGORY_STATE";
    private static final int HTTP_STATUS = 409;

    public InvalidCategoryStateException(UUID categoryId, String operation, String currentState) {
        super(String.format("Cannot %s category %s. Current state: %s", operation, categoryId, currentState));
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
