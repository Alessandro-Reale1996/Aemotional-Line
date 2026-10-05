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
		return List.copyOf(assignments);
	}



	void replaceWith(List<ConstraintAssignment> newAssignments)
	{
        Objects.requireNonNull(newAssignments, "Assignments cannot be null");

        // Checked before copying (List.copyOf would throw a NullPointerException) and before the set is cleared, so a failure changes nothing.
        if (newAssignments.stream().anyMatch(Objects::isNull))
        {
            throw new DomainException("Assignments cannot contain null.");
        }

        // Copying means the caller can't change the set through its own list later.
        List<ConstraintAssignment> copy = List.copyOf(newAssignments);

        assignments.clear();
        assignments.addAll(copy);
    }
	
	  // Only the sender's own assignments are evaluated: a constraint on one partner never blocks the other.
	  // The context is needed only when there is something to check: a sender without constraints may pass null.
	  public void ensureSatisfiedBy(UserId userId, ConstraintContext context) 
	  {
	        if (userId == null)
	        {
	            throw new DomainException("User id cannot be null.");
	        }

	        List<ConstraintAssignment> userAssignments = assignments.stream()
	            .filter(assignment -> assignment.getUserId().equals(userId))
	            .toList();

	        if (userAssignments.isEmpty())
	        {
	            return;
	        }

	        if (context == null)
	        {
	            throw new DomainException("A constraint context is required to check the sender's constraints.");
	        }

	        userAssignments
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
