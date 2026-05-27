package com.arka.model.commons.exception;

public class StorageServiceUnavailableException extends DomainException {

    public StorageServiceUnavailableException(String message) {
        super(message);
    }

    @Override
    public int getHttpStatus() {
        return 503;
    }

    @Override
    public String getCode() {
        return "STORAGE_UNAVAILABLE";
    }
}
