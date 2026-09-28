package com.aemotionalline.domain.common;

/**
 * Signals a violated business rule. It is unchecked and has a single type so that the future REST layer
 * can translate every rule violation into one consistent client error, separate from unexpected failures.
 */
public class DomainException extends RuntimeException {

    public DomainException(String message) {
        super(message);
    }
}
