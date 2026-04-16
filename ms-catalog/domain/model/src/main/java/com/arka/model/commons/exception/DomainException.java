package com.arka.model.commons.exception;

/**
 * Base class for all domain exceptions.
 * Provides HTTP status code and error code for consistent error handling.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Returns the HTTP status code associated with this exception.
     */
    public abstract int getHttpStatus();

    /**
     * Returns the error code for this exception.
     */
    public abstract String getCode();
}
