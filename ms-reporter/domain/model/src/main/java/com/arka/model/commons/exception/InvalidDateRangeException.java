package com.arka.model.commons.exception;

public class InvalidDateRangeException extends DomainException {

    public InvalidDateRangeException(String message) {
        super(message);
    }

    @Override
    public int getHttpStatus() {
        return 400;
    }

    @Override
    public String getCode() {
        return "INVALID_DATE_RANGE";
    }
}
