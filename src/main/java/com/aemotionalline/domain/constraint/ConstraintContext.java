package com.aemotionalline.domain.constraint;

import java.time.Instant;
import java.time.LocalTime;

/**
 * The circumstances a message is read or sent in. The caller supplies the local time of day (it depends on the user's
 * time zone) and the position; either may be null when no constraint needs it. {@code now} (the instant of the action,
 * from the conversation's clock) and {@code readAt} (when the message being answered was first read) are filled in by
 * the conversation, which overwrites whatever the caller put there, so the caller can't fake them. So does
 * {@code nothingToAnswer}: set by the conversation for the first message of a discussion, which answers no message, so
 * a reply delay has nothing to count from (CLAUDE.md, F7).
 * Passing everything in, rather than reading the system clock inside a constraint, keeps constraints pure and testable.
 */
public class ConstraintContext
{
    private final LocalTime time;
    private final Instant now;
    private final Instant readAt;
    private final GeoPosition position;
    private final boolean nothingToAnswer;

    public ConstraintContext(LocalTime time)
    {
        this(time, null, null, null, false);
    }

    public ConstraintContext(LocalTime time, GeoPosition position)
    {
        this(time, null, null, position, false);
    }

    private ConstraintContext(LocalTime time, Instant now, Instant readAt, GeoPosition position, boolean nothingToAnswer)
    {
        this.time = time;
        this.now = now;
        this.readAt = readAt;
        this.position = position;
        this.nothingToAnswer = nothingToAnswer;
    }

    public ConstraintContext withNow(Instant now)
    {
        return new ConstraintContext(time, now, readAt, position, nothingToAnswer);
    }

    /** A reply: when the message it answers was first read. */
    public ConstraintContext withReadAt(Instant readAt)
    {
        return new ConstraintContext(time, now, readAt, position, false);
    }

    /** The first message of a discussion: it answers no message. */
    public ConstraintContext withNothingToAnswer()
    {
        return new ConstraintContext(time, now, null, position, true);
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

    public boolean nothingToAnswer()
    {
        return nothingToAnswer;
    }
}
