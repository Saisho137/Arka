package com.arka.model.commons.exception;

public class PurchaseOrderNotFoundException extends DomainException {

    public PurchaseOrderNotFoundException(String id) {
        super("Purchase order not found with id: " + id);
    }

    @Override
    public int getHttpStatus() {
        return 404;
    }

    @Override
    public String getCode() {
        return "PURCHASE_ORDER_NOT_FOUND";
    }
}
