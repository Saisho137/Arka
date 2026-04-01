package com.arka.model.commons.exception;

public class StockNotFoundException extends DomainException {

    public StockNotFoundException(String sku) {
        super("Stock not found for SKU: " + sku);
    }

    @Override
    public int getHttpStatus() {
        return 404;
    }

    @Override
    public String getCode() {
        return "STOCK_NOT_FOUND";
    }
}
