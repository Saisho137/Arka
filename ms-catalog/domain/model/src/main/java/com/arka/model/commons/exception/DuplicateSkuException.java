package com.arka.model.commons.exception;

/**
 * Exception thrown when attempting to create a product with a SKU that already exists.
 * HTTP Status: 409 Conflict
 */
public class DuplicateSkuException extends DomainException {

    private static final String ERROR_CODE = "DUPLICATE_SKU";
    private static final int HTTP_STATUS = 409;

    public DuplicateSkuException(String sku) {
        super(String.format("Product with SKU '%s' already exists", sku));
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
