package com.arka.model.commons.exception;

public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    public abstract int getHttpStatus();

    public abstract String getCode();
}
