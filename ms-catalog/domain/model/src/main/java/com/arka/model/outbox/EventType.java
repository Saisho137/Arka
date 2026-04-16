package com.arka.model.outbox;

/**
 * Types of domain events published by ms-catalog.
 */
public enum EventType {
    PRODUCT_CREATED,
    PRODUCT_UPDATED,
    PRICE_CHANGED
}
