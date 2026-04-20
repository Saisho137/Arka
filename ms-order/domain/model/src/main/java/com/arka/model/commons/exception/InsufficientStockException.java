package com.arka.model.commons.exception;

import java.util.List;

public class InsufficientStockException extends DomainException {

    public InsufficientStockException(String message) {
        super(message);
    }

    public InsufficientStockException(List<String> failedSkus) {
        super("Insufficient stock for SKUs: " + String.join(", ", failedSkus));
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
