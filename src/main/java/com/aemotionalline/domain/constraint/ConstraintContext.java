package com.aemotionalline.domain.constraint;

import java.time.Instant;
import java.time.LocalTime;

/**
 * The circumstances a message is read or sent in, supplied by the caller. Passing them in, rather than reading the
 * system clock inside a constraint, keeps constraints pure and testable. {@code now} is the instant of the action;
 * {@code readAt} is when the message being answered was first read, and is filled in by the conversation, not the caller.
 */
public class ConstraintContext
{
    private final LocalTime time;
    private final Instant now;
    private final Instant readAt;

    public ConstraintContext(LocalTime time)
    {
        this(time, null, null);
    }

    public ConstraintContext(LocalTime time, Instant now)
    {
        this(time, now, null);
    }

    private ConstraintContext(LocalTime time, Instant now, Instant readAt)
    {
        this.time = time;
        this.now = now;
        this.readAt = readAt;
    }

    public ConstraintContext withReadAt(Instant readAt)
    {
        return new ConstraintContext(time, now, readAt);
    }

    public LocalTime time() 
    {
        return time;
    }

    public Instant now()
    {
        return now;
    }

    public Instant readAt()
    {
        return readAt;
    }
}
