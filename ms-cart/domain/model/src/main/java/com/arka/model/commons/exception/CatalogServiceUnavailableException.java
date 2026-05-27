package com.arka.model.commons.exception;

public class CatalogServiceUnavailableException extends DomainException {

    public CatalogServiceUnavailableException(String reason) {
        super("Catalog service unavailable: " + reason);
    }

    @Override
    public int getHttpStatus() {
        return 503;
    }

    @Override
    public String getCode() {
        return "CATALOG_SERVICE_UNAVAILABLE";
    }
}
