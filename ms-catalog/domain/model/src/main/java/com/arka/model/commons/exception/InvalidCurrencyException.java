package com.arka.model.commons.exception;

/**
 * Exception thrown when an unsupported currency is provided.
 * HTTP Status: 400 Bad Request
 */
public class InvalidCurrencyException extends DomainException {

    private static final String ERROR_CODE = "INVALID_CURRENCY";
    private static final int HTTP_STATUS = 400;

    public InvalidCurrencyException(String currency) {
        super(String.format("Unsupported currency: %s. Supported currencies: COP, USD, PEN, CLP", currency));
    }

    public InvalidCurrencyException(String message, String currency) {
        super(String.format("%s: %s", message, currency));
    }

    @Override
    public int getHttpStatus() {
        return HTTP_STATUS;
    }

    @Override
    public String getCode() {
        return ERROR_CODE;
    }
}
