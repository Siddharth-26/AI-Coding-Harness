package com.lld.core.exception;

/** Base for every business-rule failure. Callers can catch this one type at the edge. */
public class DomainException extends RuntimeException {
    public DomainException(String message) {
        super(message);
    }
}
