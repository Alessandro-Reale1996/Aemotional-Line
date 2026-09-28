package com.aemotionalline.domain.constraint;

import java.time.LocalTime;

/**
 * The circumstances a message is sent in, supplied by the caller. Passing them in, rather than reading the
 * system clock inside a constraint, keeps constraints pure and testable.
 */
public class ConstraintContext
{
    private final LocalTime time;

    public ConstraintContext(LocalTime time)
    {
        this.time = time;
    }

    public LocalTime time() 
    {
        return time;
    }
}
    

