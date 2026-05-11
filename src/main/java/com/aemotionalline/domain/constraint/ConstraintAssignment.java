package com.aemotionalline.domain.constraint;

import com.aemotionalline.domain.user.UserId;

public class ConstraintAssignment {

    private final UserId userId;
    private final Constraint constraint;

    public ConstraintAssignment(UserId userId, Constraint constraint) 
    {
	    if (userId == null || constraint == null) 
	    {
	        throw new IllegalArgumentException("Assignment cannot be null");
	    }

        this.userId = userId;
        this.constraint = constraint;
    }

	public UserId getUserId()
	{
		return userId;
	}

	public Constraint getConstraint()
	{
		return constraint;
	}

    
}
