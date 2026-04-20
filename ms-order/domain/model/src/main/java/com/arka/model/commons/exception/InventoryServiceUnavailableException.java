package com.arka.model.commons.exception;

public class InventoryServiceUnavailableException extends DomainException {

    public InventoryServiceUnavailableException(String message) {
        super(message);
    }

    @Override
    public int getHttpStatus() {
        return 503;
    }

    @Override
    public String getCode() {
        return "INVENTORY_UNAVAILABLE";
    }
}
