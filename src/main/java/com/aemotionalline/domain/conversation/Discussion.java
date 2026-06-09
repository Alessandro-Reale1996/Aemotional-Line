package com.aemotionalline.domain.conversation;

import java.util.ArrayList;
import java.util.List;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.message.Message;

public class Discussion
{
	private final DiscussionId id;
	private final List<Message> messages;
	
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
