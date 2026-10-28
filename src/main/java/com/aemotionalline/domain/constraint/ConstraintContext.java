package com.aemotionalline.domain.constraint;

import java.time.Instant;
import java.time.LocalTime;

/**
 * The circumstances a message is read or sent in. The caller supplies the local time of day (it depends on the user's
 * time zone) and the position; either may be null when no constraint needs it. {@code now} (the instant of the action,
 * from the conversation's clock) and {@code readAt} (when the message being answered was first read) are filled in by
 * the conversation, which overwrites whatever the caller put there, so the caller can't fake them.
 * Passing everything in, rather than reading the system clock inside a constraint, keeps constraints pure and testable.
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

    public ConstraintContext(LocalTime time, GeoPosition position)
    {
        this(time, null, null, position);
    }

    private ConstraintContext(LocalTime time, Instant now, Instant readAt, GeoPosition position)
    {
        this.time = time;
        this.now = now;
        this.readAt = readAt;
        this.position = position;
    }

    public ConstraintContext withNow(Instant now)
    {
        return new ConstraintContext(time, now, readAt, position);
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
