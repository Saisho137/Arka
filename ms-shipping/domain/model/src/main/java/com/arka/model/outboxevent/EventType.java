package com.arka.model.outboxevent;

public enum EventType {
    SHIPPING_DISPATCHED("ShippingDispatched");

    private final String value;

    EventType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
