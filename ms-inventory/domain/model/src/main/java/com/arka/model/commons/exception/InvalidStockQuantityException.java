package com.arka.model.commons.exception;

public class InvalidStockQuantityException extends DomainException {

    public InvalidStockQuantityException(String sku, int quantity, int reservedQuantity) {
        super("Invalid stock quantity for SKU: " + sku + ". Quantity " + quantity + " cannot be less than reserved quantity " + reservedQuantity);
    }

    public InvalidStockQuantityException(String sku, int quantity, String reason) {
        super("Invalid stock quantity for SKU: " + sku + ". Quantity " + quantity + ": " + reason);
    }

    @Override
    public int getHttpStatus() {
        return 409;
    }

    @Override
    public String getCode() {
        return "INVALID_STOCK_QUANTITY";
    }
}
