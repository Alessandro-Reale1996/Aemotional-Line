package com.aemotionalline.domain.constraint;

import java.time.LocalTime;

/** Forbids sending before a given time of day. */
public class TimeConstraint implements Constraint
{
	private final LocalTime notBefore;

    public TimeConstraint (LocalTime notBefore) 
    {
        this.notBefore = notBefore;
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
