package com.aemotionalline.domain.conversation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.message.Message;
import com.aemotionalline.domain.negotiation.Proposal;
import com.aemotionalline.domain.user.UserId;

/**
 * An ordered exchange of messages inside a conversation. It keeps the agreement it was opened under
 * and enforces turn-taking, so the partners are forced to answer each other rather than write in bursts.
 */
public class Discussion
{
	private final DiscussionId id;
	private final List<Message> messages;
	
	private UserId lastSender;
	private Proposal agreement;
	
	public Discussion(DiscussionId id, Proposal agreement)
	{
		this.id = id;
		this.agreement = agreement;
		
		this.messages = new ArrayList<Message>();
	}

	public DiscussionId getId()
	{
		return id;
	}

	public List<Message> getMessages()
	{
		return List.copyOf(messages);
	}
	
	public UserId getLastSender() 
	{
		return lastSender;
	}
	
	public Proposal getAgreement() 
	{
		return agreement;
	}

	// Rejecting the same sender twice in a row is what makes the exchange turn-based.
	public void setLastSender(UserId lastSender) 
	{
		if (lastSender.equals(this.lastSender)) 
		{
			throw new DomainException("A new message can't be sent if an answer wasn't received.");
		}
		
		this.lastSender = lastSender;
		
		if(lastSender != this.lastSender)
		{
			throw new DomainException("The sender was not registered as the last sender of the discussion.");
		}
	}
	
	

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}

		if (!(o instanceof Discussion other))
		{
			return false;
		}

		return Objects.equals(id, other.id);
	}
	
	@Override
	public int hashCode()
	{
		return Objects.hash(id);
	}
	
	public void addMessage(Message message)
	{
		if (message == null)
		{
			throw new DomainException("Message can't be null.");
		}
		
		message.ensureReadyToSend();
		this.messages.add(message);
		
		if(!messages.contains(message))
		{
			throw new DomainException("Message was not added to the discussion.");
		}
	}
	
}
