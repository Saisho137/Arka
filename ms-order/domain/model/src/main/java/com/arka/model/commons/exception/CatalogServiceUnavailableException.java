package com.arka.model.commons.exception;

public class CatalogServiceUnavailableException extends DomainException {

    public CatalogServiceUnavailableException(String message) {
        super(message);
    }

    @Override
    public int getHttpStatus() {
        return 503;
    }

    @Override
    public String getCode() {
        return "CATALOG_UNAVAILABLE";
    }
}
