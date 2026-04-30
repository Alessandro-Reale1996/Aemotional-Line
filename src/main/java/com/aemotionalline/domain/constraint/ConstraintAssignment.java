package com.aemotionalline.domain.constraint;

import com.aemotionalline.domain.user.UserId;

public class ConstraintAssignment {

    private final UserId userId;
    private final TimeConstraint constraint;

    public ConstraintAssignment(UserId userId, TimeConstraint constraint) 
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

	public TimeConstraint getConstraint()
	{
		return constraint;
	}

    
}
