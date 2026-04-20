package com.arka.model.commons.exception;

public class AccessDeniedException extends DomainException {

    public AccessDeniedException(String message) {
        super(message);
    }

    @Override
    public int getHttpStatus() {
        return 403;
    }

    @Override
    public String getCode() {
        return "ACCESS_DENIED";
    }
}
