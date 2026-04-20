package com.arka.api.handler;

import com.arka.api.dto.ErrorResponse;
import com.arka.model.commons.exception.DomainException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

/**
 * Global exception handler for ms-catalog.
 * Adapted from ms-inventory (reusability.md #8).
 * Translates exceptions to standardized ErrorResponse DTOs.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handles Bean Validation errors (e.g., @NotBlank, @Positive).
     * Returns 400 Bad Request with field-level error details.
     */
    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleValidation(WebExchangeBindException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return Mono.just(ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("VALIDATION_ERROR", message)));
    }

    /**
     * Handles invalid input errors (e.g., malformed JSON, type mismatches).
     * Returns 400 Bad Request.
     */
    @ExceptionHandler(ServerWebInputException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleBadInput(ServerWebInputException ex) {
        String reason = ex.getReason() != null ? ex.getReason() : "Malformed request";
        return Mono.just(ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("BAD_REQUEST", "Invalid request: " + reason)));
    }

    /**
     * Handles domain-specific exceptions (ProductNotFoundException, DuplicateSkuException, etc.).
     * Returns HTTP status and error code from the exception.
     */
    @ExceptionHandler(DomainException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleDomain(DomainException ex) {
        return Mono.just(ResponseEntity
                .status(ex.getHttpStatus())
                .body(new ErrorResponse(ex.getCode(), ex.getMessage())));
    }

    /**
     * Handles IllegalArgumentException (e.g., invalid Money currency, invalid rating).
     * Returns 400 Bad Request.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleIllegalArgument(IllegalArgumentException ex) {
        return Mono.just(ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_ARGUMENT", ex.getMessage())));
    }

    /**
     * Handles IllegalStateException (e.g., invalid state transitions).
     * Returns 409 Conflict.
     */
    @ExceptionHandler(IllegalStateException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleIllegalState(IllegalStateException ex) {
        return Mono.just(ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("ILLEGAL_STATE", ex.getMessage())));
    }

    /**
     * Handles all unexpected exceptions.
     * Returns 500 Internal Server Error with a generic message (no internal details exposed).
     */
    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ErrorResponse>> handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        return Mono.just(ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred")));
    }
}
