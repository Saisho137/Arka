package com.arka.model.commons.exception;

public class InsufficientStockException extends DomainException {

    public InsufficientStockException(String sku, int requested, int available) {
        super("Insufficient stock for SKU: " + sku + ". Requested: " + requested + ", Available: " + available);
    }

    @Override
    public int getHttpStatus() {
        return 409;
    }

    @Override
    public String getCode() {
        return "INSUFFICIENT_STOCK";
    }
}
