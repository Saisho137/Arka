package com.arka.model.commons.exception;

public class OptimisticLockException extends DomainException {

    public OptimisticLockException(String sku) {
        super("Concurrent modification detected for SKU: " + sku + ". Please retry the operation.");
    }

    @Override
    public int getHttpStatus() {
        return 409;
    }

    @Override
    public String getCode() {
        return "CONCURRENT_MODIFICATION";
    }
}
