package com.arka.model.commons.exception;

public class ExcessiveReleaseException extends DomainException {

    public ExcessiveReleaseException(String sku, int releaseAmount, int reservedQuantity) {
        super("Cannot release " + releaseAmount + " units for SKU: " + sku + ". Only " + reservedQuantity + " units are reserved");
    }

    @Override
    public int getHttpStatus() {
        return 409;
    }

    @Override
    public String getCode() {
        return "EXCESSIVE_RELEASE";
    }
}
