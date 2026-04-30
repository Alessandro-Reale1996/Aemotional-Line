package com.aemotionalline.domain.constraint;

import java.time.LocalTime;

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
        return !context.time().isBefore(notBefore);
    }

    @Override
    public String getDescription() 
    {
        return "Allowed after " + notBefore;
    }
	  
}
