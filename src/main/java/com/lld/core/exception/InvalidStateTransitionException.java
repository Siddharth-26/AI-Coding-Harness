package com.lld.core.exception;

/** A state machine refused a transition, e.g. Order DELIVERED -> PREPARING. */
public class InvalidStateTransitionException extends ConflictException {
    public InvalidStateTransitionException(String subject, Enum<?> from, Enum<?> to) {
        super(subject + " cannot move from " + from + " to " + to);
    }
}
