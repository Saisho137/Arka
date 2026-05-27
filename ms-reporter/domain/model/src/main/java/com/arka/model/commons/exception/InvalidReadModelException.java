package com.arka.model.commons.exception;

public class InvalidReadModelException extends DomainException {

    public InvalidReadModelException(String readModel) {
        super("Invalid read model: " + readModel);
    }

    @Override
    public int getHttpStatus() {
        return 400;
    }

    @Override
    public String getCode() {
        return "INVALID_READ_MODEL";
    }
}
