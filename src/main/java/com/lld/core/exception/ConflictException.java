package com.lld.core.exception;

/** Valid request, but the current state of the world rejects it (seat taken, lot full, duplicate). Maps to HTTP 409. */
public class ConflictException extends DomainException {
    public ConflictException(String message) {
        super(message);
    }
}
