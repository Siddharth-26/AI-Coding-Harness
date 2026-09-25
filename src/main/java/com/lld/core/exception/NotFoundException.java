package com.lld.core.exception;

/** Entity id does not exist. Maps to HTTP 404 if this ever sits behind an API. */
public class NotFoundException extends DomainException {
    public NotFoundException(String entity, String id) {
        super(entity + " not found: " + id);
    }
}
