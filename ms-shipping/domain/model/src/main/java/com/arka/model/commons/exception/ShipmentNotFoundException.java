package com.arka.model.commons.exception;

public class ShipmentNotFoundException extends DomainException {

    public ShipmentNotFoundException(String orderId) {
        super("Shipment not found for orderId: " + orderId);
    }

    @Override
    public int getHttpStatus() {
        return 404;
    }

    @Override
    public String getCode() {
        return "SHIPMENT_NOT_FOUND";
    }
}
