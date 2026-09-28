package com.aemotionalline.domain.constraint;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.user.UserId;

/**
 * The constraints currently in force for a conversation. Updates replace the whole set instead of merging,
 * so the set always equals the last change request both partners approved.
 */
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
			throw new DomainException("The list of constraints wasn't cleared.");
		}
        assignments.addAll(newAssignments);
    }
	
	  // Only the sender's own assignments are evaluated: a constraint on one partner never blocks the other.
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
