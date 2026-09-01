package com.aemotionalline.domain.conversation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.message.Message;
import com.aemotionalline.domain.user.UserId;

public class Discussion
{
	private final DiscussionId id;
	private final List<Message> messages;
	private UserId LastSender;
	
	public Discussion(DiscussionId id)
	{
		this.id = id;
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
		return LastSender;
	}

	public void setLastSender(UserId lastSender) 
	{
		if (lastSender.equals(this.LastSender)) 
		{
			throw new DomainException("A new message can't be send, if an aswer wasn't recived.");
		}
		
		LastSender = lastSender;
		
		if(lastSender != this.LastSender)
		{
			throw new DomainException("The sender wan't appoited as last sender in the discussion.");
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
			throw new DomainException("Paragraph was not added in the message.");
		}
	}
	
}
