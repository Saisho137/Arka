package com.arka.model.commons.exception;

public class SupplierNotFoundException extends DomainException {

    public SupplierNotFoundException(String id) {
        super("Supplier not found with id: " + id);
    }

    @Override
    public int getHttpStatus() {
        return 404;
    }

    @Override
    public String getCode() {
        return "SUPPLIER_NOT_FOUND";
    }
}
