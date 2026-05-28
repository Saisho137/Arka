package com.arka.model.payment.event;

public enum EventType {
    PAYMENT_PROCESSED("PaymentProcessed"),
    PAYMENT_FAILED("PaymentFailed");

    private final String value;

    EventType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
