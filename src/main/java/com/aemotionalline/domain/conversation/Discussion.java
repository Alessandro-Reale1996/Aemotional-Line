package com.aemotionalline.domain.conversation;

import java.util.ArrayList;
import java.util.List;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.message.Message;

public class Discussion
{
	private final DiscussionId id;
	private final List<Message> messageIds;
	
	public Discussion(DiscussionId id)
	{
		this.id = id;
		this.messageIds = new ArrayList<Message>();
	}

	public DiscussionId getId()
	{
		return id;
	}

	public List<Message> getMessageIds()
	{
		return List.copyOf(messageIds);
	}
	
	public void addMessage(Message message)
	{
		if (message == null)
		{
			throw new DomainException("Message can't be null.");
		}
		
		message.ensureReadyToSend();
		this.messageIds.add(message);
		
		if(!messageIds.contains(message))
		{
			throw new DomainException("Paragraph was not added in the message.");
		}
	}
	
}
