package com.aemotionalline.domain.conversation;

import java.util.ArrayList;
import java.util.List;

import com.aemotionalline.domain.agreement.Agreement;
import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.user.UserId;
import com.aemotionalline.domain.constraint.ConstraintAssignment;
import com.aemotionalline.domain.constraint.ConstraintChangeRequest;
import com.aemotionalline.domain.constraint.ConstraintContext;

public class Conversation
{
	private final Long id;
	private final Long coupleId;
	private final Agreement agreement;
	private ConversationStatus status;
	private List<ConstraintAssignment> activeConstraints = new ArrayList<>();
	
	public Conversation(Long id, Long coupleId, Agreement agreement)
	{
		
        if (id == null) 
        {
            throw new DomainException("Conversation id cannot be null");
        }

        if (coupleId == null) 
        {
            throw new DomainException("Couple id cannot be null");
        }

        if (agreement == null)
        {
            throw new DomainException("Agreement cannot be null");
        }
		
		this.id = id;
		this.coupleId = coupleId;
		this.agreement = agreement;
		this.status = ConversationStatus.PENDING_AGREEMENT;
	}
	
	
	
	public Long getId()
	{
		return id;
	}



	public Long getCoupleId()
	{
		return coupleId;
	}



	public Agreement getAgreement()
	{
		return agreement;
	}



	public ConversationStatus getStatus()
	{
		return status;
	}
	
	
	public List<ConstraintAssignment> getActiveConstraints()
	{
		return activeConstraints;
	}

	
	

	public void acceptAgrement(UserId userId, Couple couple )
	{
		this.agreement.accept(userId, couple);
		
		if (agreement.isAccepted())
		{
			this.status = ConversationStatus.ACTIVE;
		}
	}
	
	public boolean canSendMessage(UserId userId, Couple couple)
	{
		return  couple.isPartner(userId) && status == ConversationStatus.ACTIVE && agreement.isAccepted();	
	}
	
	public void ensureCanSendMessage(UserId userId, Couple couple)
	{
		if (!canSendMessage(userId, couple))
		{
			throw new DomainException("User cannot send messages in the current conversation state");
		}
	}
	
	public void applyConstraints(ConstraintChangeRequest request, Couple couple) 
	{

	    if (!request.isFullyApproved(couple)) 
	    {
	        throw new DomainException("Constraints not fully approved");
	    }

	    activeConstraints.clear();
	    
	    if (!activeConstraints.isEmpty())
		{
			throw new DomainException("The conversation with id " + id + "didn't clear his activeConstraints's list.");
		}
	    
	    activeConstraints.addAll(request.assignments());
	}
	
	private void ensureConstraintsAreSatisfied(UserId userId, ConstraintContext context) 
	{

	    activeConstraints.stream()
	        .filter(a -> a.getUserId().equals(userId))
	        .forEach(a -> 
	        						{
							            if (!a.getConstraint().isSatisfied(context)) 
							            {
							                throw new DomainException("Constraint violated");
							            }
        							}
    						 );
	}
	
	public void sendMessage(UserId userId, Couple couple, ConstraintContext context) 
	{

	    ensureCanSendMessage(userId, couple);
	    ensureConstraintsAreSatisfied(userId, context);

	    // TODO: Add message
	}
	
}
