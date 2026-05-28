package com.arka.model.outboxevent;

public enum EventType {
    PRODUCT_CREATED("ProductCreated"),
    PRODUCT_UPDATED("ProductUpdated"),
    PRICE_CHANGED("PriceChanged");

    private final String value;

    EventType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
