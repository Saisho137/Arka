package com.arka.model.commons.exception;

public class StockConstraintViolationException extends DomainException {

    public StockConstraintViolationException(String sku) {
        super("Stock constraint violation for SKU: " + sku + ". Operation would result in negative stock.");
    }

    @Override
    public int getHttpStatus() {
        return 409;
    }

    @Override
    public String getCode() {
        return "STOCK_CONSTRAINT_VIOLATION";
    }
}
