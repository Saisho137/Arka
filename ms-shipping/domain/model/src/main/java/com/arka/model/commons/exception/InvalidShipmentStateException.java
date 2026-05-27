package com.arka.model.commons.exception;

public class InvalidShipmentStateException extends DomainException {

    public InvalidShipmentStateException(String message) {
        super(message);
    }

    @Override
    public int getHttpStatus() {
        return 409;
    }

    @Override
    public String getCode() {
        return "INVALID_SHIPMENT_STATE";
    }
}
