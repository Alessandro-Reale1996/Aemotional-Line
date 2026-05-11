package com.aemotionalline.domain.constraint;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.user.UserId;

public class ConstraintSet
{
	private final List<ConstraintAssignment> assignments;

	public ConstraintSet()
	{
		super();
		this.assignments = new ArrayList<ConstraintAssignment>();
	}
	
	
	
	public List<ConstraintAssignment> getAssignments()
	{
		return assignments;
	}



	public void replaceWith(List<ConstraintAssignment> newAssignments) 
	{
        Objects.requireNonNull(newAssignments, "Assignments cannot be null");

        assignments.clear();
        if (!assignments.isEmpty())
		{
			throw new DomainException("The list of costraints wasn't cleared.");
		}
        assignments.addAll(newAssignments);
    }
	
	  public void ensureSatisfiedBy(UserId userId, ConstraintContext context) 
	  {
	        Objects.requireNonNull(userId, "User id cannot be null");
	        Objects.requireNonNull(context, "Constraint context cannot be null");

	        assignments.stream()
	            .filter(assignment -> assignment.getUserId().equals(userId))
	            .forEach(assignment -> 
						            {
						                if (!assignment.getConstraint().isSatisfied(context)) 
						                {
						                    throw new DomainException("Constraint violated");
						                }
						            }
					            );
	    }

	  public boolean isEmpty() 
	  {
	        return assignments.isEmpty();
	    }
	
	
}
