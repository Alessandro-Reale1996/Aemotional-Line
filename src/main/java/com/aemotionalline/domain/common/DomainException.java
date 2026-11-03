package com.aemotionalline.domain.common;

import java.util.Objects;

/**
 * Signals a violated business rule. It is unchecked and has a single type so that the REST layer can translate every
 * rule violation into one consistent client error, separate from unexpected failures; its code tells the client which
 * rule (see ErrorCode). Without a code it is a programming guard: RULE_VIOLATED.
 */
public class DomainException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode code;

    public DomainException(String message) {
        this(ErrorCode.RULE_VIOLATED, message);
    }

    public DomainException(ErrorCode code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public ErrorCode getCode() {
        return code;
    }
}
