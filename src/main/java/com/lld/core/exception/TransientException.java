package com.lld.core.exception;

/**
 * An infrastructure failure that may succeed if tried again: timeout, 503, connection reset,
 * lock wait timeout. Deliberately NOT a DomainException: business-rule failures (validation,
 * conflict, invalid transition) must never be retried.
 *
 * Retrying is only safe when the operation is idempotent (see IdempotencyStore).
 */
public class TransientException extends RuntimeException {

    public TransientException(String message) {
        super(message);
    }

    public TransientException(String message, Throwable cause) {
        super(message, cause);
    }
}
