package com.arka.model.commons.exception;

public class ShippingCarrierUnavailableException extends DomainException {

    public ShippingCarrierUnavailableException(String carrier, String reason) {
        super("Carrier " + carrier + " unavailable: " + reason);
    }

    @Override
    public int getHttpStatus() {
        return 503;
    }

    @Override
    public String getCode() {
        return "CARRIER_UNAVAILABLE";
    }
}
