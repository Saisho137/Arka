package com.arka.model.outbox;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EventType {
    PURCHASE_ORDER_CREATED("PurchaseOrderCreated"),
    PURCHASE_ORDER_SENT("PurchaseOrderSent");

    private final String value;
}
