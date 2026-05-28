package com.arka.model.commons.exception;

public class DuplicateEmailException extends DomainException {

    public DuplicateEmailException(String email) {
        super("Supplier with email already exists: " + email);
    }

    @Override
    public int getHttpStatus() {
        return 409;
    }

    @Override
    public String getCode() {
        return "DUPLICATE_EMAIL";
    }
}
