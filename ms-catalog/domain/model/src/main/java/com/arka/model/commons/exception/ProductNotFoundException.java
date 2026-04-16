package com.arka.model.commons.exception;

import java.util.UUID;

/**
 * Exception thrown when a product is not found by ID or SKU.
 * HTTP Status: 404 Not Found
 */
public class ProductNotFoundException extends DomainException {

    private static final String ERROR_CODE = "PRODUCT_NOT_FOUND";
    private static final int HTTP_STATUS = 404;

    public ProductNotFoundException(String message) {
        super(message);
    }

    public ProductNotFoundException(UUID productId) {
        super(String.format("Product not found with ID: %s", productId));
    }

    public ProductNotFoundException(String field, String value) {
        super(String.format("Product not found with %s: %s", field, value));
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
