package com.arka.model.commons.exception;

public class ProductNotFoundException extends DomainException {

    public ProductNotFoundException(String sku) {
        super("Product not found: " + sku);
    }

    @Override
    public int getHttpStatus() {
        return 404;
    }

    @Override
    public String getCode() {
        return "PRODUCT_NOT_FOUND";
    }
}
