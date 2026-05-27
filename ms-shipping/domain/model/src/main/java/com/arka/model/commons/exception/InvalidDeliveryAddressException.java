package com.arka.model.commons.exception;

public class InvalidDeliveryAddressException extends DomainException {

    public InvalidDeliveryAddressException(String reason) {
        super("Invalid delivery address: " + reason);
    }

    @Override
    public int getHttpStatus() {
        return 400;
    }

    @Override
    public String getCode() {
        return "INVALID_DELIVERY_ADDRESS";
    }
}
