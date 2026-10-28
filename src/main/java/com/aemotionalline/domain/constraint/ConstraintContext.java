package com.aemotionalline.domain.constraint;

import java.time.Instant;
import java.time.LocalTime;

/**
 * The circumstances a message is read or sent in, supplied by the caller. Passing them in, rather than reading the
 * system clock inside a constraint, keeps constraints pure and testable. {@code now} is the instant of the action,
 * {@code position} where the user is (both may be null when no constraint needs them);
 * {@code readAt} is when the message being answered was first read, and is filled in by the conversation, not the caller.
 */
public class ConstraintContext
{
    private final LocalTime time;
    private final Instant now;
    private final Instant readAt;
    private final GeoPosition position;

    public ConstraintContext(LocalTime time)
    {
        this(time, null, null, null);
    }

    public ConstraintContext(LocalTime time, Instant now)
    {
        this(time, now, null, null);
    }

    public ConstraintContext(LocalTime time, Instant now, GeoPosition position)
    {
        this(time, now, null, position);
    }

    private ConstraintContext(LocalTime time, Instant now, Instant readAt, GeoPosition position)
    {
        this.time = time;
        this.now = now;
        this.readAt = readAt;
        this.position = position;
    }

    public ConstraintContext withReadAt(Instant readAt)
    {
        return new ConstraintContext(time, now, readAt, position);
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

    public GeoPosition position()
    {
        return position;
    }
}
