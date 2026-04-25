package com.aemotionalline.domain.conversation;

import com.aemotionalline.domain.agreement.Agreement;
import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.user.UserId;

public class Conversation
{
	private final Long id;
	private final Long coupleId;
	private final Agreement agreement;
	private ConversationStatus status;
	
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



	public void acceptAgrement(UserId userId, Couple couple )
	{
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
	
}
