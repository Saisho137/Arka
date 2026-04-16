package com.arka.model.commons.exception;

import java.util.UUID;

/**
 * Exception thrown when attempting an invalid state transition on a product.
 * HTTP Status: 409 Conflict
 */
public class InvalidProductStateException extends DomainException {

    private static final String ERROR_CODE = "INVALID_PRODUCT_STATE";
    private static final int HTTP_STATUS = 409;

    public InvalidProductStateException(UUID productId, String operation, String currentState) {
        super(String.format("Cannot %s product %s. Current state: %s", operation, productId, currentState));
    }

    public InvalidProductStateException(String message) {
        super(message);
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
