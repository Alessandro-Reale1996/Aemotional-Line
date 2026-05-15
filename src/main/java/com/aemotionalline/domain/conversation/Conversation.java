package com.aemotionalline.domain.conversation;

import java.util.ArrayList;
import java.util.List;

import com.aemotionalline.domain.agreement.Agreement;
import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.message.Message;
import com.aemotionalline.domain.user.UserId;
import com.aemotionalline.domain.constraint.ConstraintAssignment;
import com.aemotionalline.domain.constraint.ConstraintChangeRequest;
import com.aemotionalline.domain.constraint.ConstraintContext;
import com.aemotionalline.domain.constraint.ConstraintSet;

public class Conversation
{
	private final Long id;
	private final Long coupleId;
	private final Agreement agreement;
	private ConversationStatus status;
	private final ConstraintSet constraintSet;
	private final List<Message> messages;
	
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
		this.constraintSet = new ConstraintSet();
		this.messages = new ArrayList<>();
		
		if (constraintSet == null)
        {
            throw new DomainException("ConstraintSet cannot be null");
        }
		
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
	
	
	
	public ConstraintSet getConstraintSet() {
		return constraintSet;
	}
	
	public List<Message> getMessages()
	{
		return List.copyOf(messages);
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

	    constraintSet.replaceWith(request.assignments());
	}
	
	
	
	public void sendMessage(Message message, Couple couple, ConstraintContext context) 
	{
		if (message == null)
		{
			throw new DomainException("Message can't be null.");
		}
		
		message.ensureReadyToSend();

	    ensureCanSendMessage(message.getSenderId(), couple);

	    constraintSet.ensureSatisfiedBy(message.getSenderId(), context);
	    
	    messages.add(message);
	}
	
}
