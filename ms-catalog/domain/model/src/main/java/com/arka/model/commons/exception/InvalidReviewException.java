package com.arka.model.commons.exception;

/**
 * Exception thrown when a review has invalid data (e.g., rating out of range, missing fields).
 * HTTP Status: 400 Bad Request
 */
public class InvalidReviewException extends DomainException {

    private static final String ERROR_CODE = "INVALID_REVIEW";
    private static final int HTTP_STATUS = 400;

    public InvalidReviewException(String message) {
        super(message);
    }

    public InvalidReviewException(String field, String reason) {
        super(String.format("Invalid review: %s %s", field, reason));
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
