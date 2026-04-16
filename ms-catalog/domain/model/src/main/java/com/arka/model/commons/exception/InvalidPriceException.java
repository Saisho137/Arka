package com.arka.model.commons.exception;

import java.math.BigDecimal;

/**
 * Exception thrown when a product price is invalid (e.g., negative or zero).
 * HTTP Status: 400 Bad Request
 */
public class InvalidPriceException extends DomainException {

    private static final String ERROR_CODE = "INVALID_PRICE";
    private static final int HTTP_STATUS = 400;

    public InvalidPriceException(BigDecimal price, String reason) {
        super(String.format("Invalid price %s: %s", price, reason));
    }

    public InvalidPriceException(String sku, BigDecimal price, String reason) {
        super(String.format("Invalid price for SKU %s: %s %s", sku, price, reason));
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
