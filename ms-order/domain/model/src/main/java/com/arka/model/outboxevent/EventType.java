package com.arka.model.outboxevent;

public enum EventType {
    ORDER_CREATED("OrderCreated"),
    ORDER_CONFIRMED("OrderConfirmed"),
    ORDER_STATUS_CHANGED("OrderStatusChanged"),
    ORDER_CANCELLED("OrderCancelled");

    private final String value;

    EventType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
