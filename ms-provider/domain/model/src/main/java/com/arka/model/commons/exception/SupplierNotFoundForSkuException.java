package com.arka.model.commons.exception;

public class SupplierNotFoundForSkuException extends DomainException {

    public SupplierNotFoundForSkuException(String sku) {
        super("No active preferred supplier found for SKU: " + sku);
    }

    @Override
    public int getHttpStatus() {
        return 422;
    }

    @Override
    public String getCode() {
        return "NO_SUPPLIER_FOR_SKU";
    }
}
