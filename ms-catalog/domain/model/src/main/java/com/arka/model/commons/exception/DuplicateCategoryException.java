package com.arka.model.commons.exception;

/**
 * Exception thrown when attempting to create a category with a name that already exists.
 * HTTP Status: 409 Conflict
 */
public class DuplicateCategoryException extends DomainException {

    private static final String ERROR_CODE = "DUPLICATE_CATEGORY";
    private static final int HTTP_STATUS = 409;

    public DuplicateCategoryException(String categoryName) {
        super(String.format("Category with name '%s' already exists", categoryName));
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
