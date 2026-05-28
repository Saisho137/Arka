package com.arka.model.commons.exception;

public class InvalidStateTransitionException extends DomainException {

    public InvalidStateTransitionException(String from, String to) {
        super("Invalid state transition from " + from + " to " + to);
    }

    @Override
    public int getHttpStatus() {
        return 422;
    }

    @Override
    public String getCode() {
        return "INVALID_STATE_TRANSITION";
    }
}
