package com.aemotionalline.domain.constraint;

import java.time.LocalTime;
import java.util.Objects;

/** Forbids acting (by default, sending) before a given time of day. */
public class TimeConstraint implements Constraint
{
	private final LocalTime notBefore;
	private final ConstraintScope scope;

    public TimeConstraint (LocalTime notBefore) 
    {
        this(notBefore, ConstraintScope.WRITE);
    }

    public TimeConstraint (LocalTime notBefore, ConstraintScope scope) 
    {
        this.notBefore = notBefore;
        this.scope = Objects.requireNonNull(scope);
    }

    @Override
    public ConstraintScope getScope()
    {
        return scope;
    }

    @Override
    public boolean isSatisfied(ConstraintContext context) 
    {
        // Negated isBefore makes the boundary inclusive: sending exactly at notBefore is allowed.
        return !context.time().isBefore(notBefore);
    }

    @Override
    public String getDescription() 
    {
        return "Allowed after " + notBefore;
    }
	  
}
