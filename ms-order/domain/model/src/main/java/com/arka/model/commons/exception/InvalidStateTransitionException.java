package com.arka.model.commons.exception;

public class InvalidStateTransitionException extends DomainException {

    public InvalidStateTransitionException(String message) {
        super(message);
    }

    @Override
    public int getHttpStatus() {
        return 409;
    }

    @Override
    public String getCode() {
        return "INVALID_STATE_TRANSITION";
    }
}
