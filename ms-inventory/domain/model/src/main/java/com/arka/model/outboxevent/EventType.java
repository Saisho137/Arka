package com.arka.model.outboxevent;

public enum EventType {
    STOCK_RESERVED("StockReserved"),
    STOCK_RESERVE_FAILED("StockReserveFailed"),
    STOCK_RELEASED("StockReleased"),
    STOCK_UPDATED("StockUpdated"),
    STOCK_DEPLETED("StockDepleted");

    private final String value;

    EventType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
