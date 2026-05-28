package com.arka.model.purchaseorder;

import java.util.Set;

public enum PurchaseOrderStatus {
    PENDING,
    SENT,
    CONFIRMED,
    PARTIALLY_RECEIVED,
    RECEIVED,
    CANCELLED;

    public boolean canTransitionTo(PurchaseOrderStatus target) {
        return getAllowedTransitions().contains(target);
    }

    private Set<PurchaseOrderStatus> getAllowedTransitions() {
        return switch (this) {
            case PENDING -> Set.of(SENT, CANCELLED);
            case SENT -> Set.of(CONFIRMED, CANCELLED);
            case CONFIRMED -> Set.of(PARTIALLY_RECEIVED, RECEIVED);
            case PARTIALLY_RECEIVED -> Set.of(RECEIVED);
            case RECEIVED, CANCELLED -> Set.of();
        };
    }
}
