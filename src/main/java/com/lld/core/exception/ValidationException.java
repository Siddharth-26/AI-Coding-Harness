package com.lld.core.exception;

/** Bad input from the caller. Maps to HTTP 400. */
public class ValidationException extends DomainException {
    public ValidationException(String message) {
        super(message);
    }
}
