package com.arka.model.commons.exception;

public class OrderNotFoundException extends DomainException {

    public OrderNotFoundException(String message) {
        super(message);
    }

    @Override
    public int getHttpStatus() {
        return 404;
    }

    @Override
    public String getCode() {
        return "ORDER_NOT_FOUND";
    }
}
