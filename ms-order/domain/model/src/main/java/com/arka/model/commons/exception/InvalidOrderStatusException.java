package com.arka.model.commons.exception;

public class InvalidOrderStatusException extends DomainException {

    public InvalidOrderStatusException(String message) {
        super(message);
    }

    @Override
    public int getHttpStatus() {
        return 400;
    }

    @Override
    public String getCode() {
        return "INVALID_ORDER_STATUS";
    }
}
