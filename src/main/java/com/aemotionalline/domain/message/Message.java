package com.aemotionalline.domain.message;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.user.UserId;

public class Message
{
	private final UserId senderId;
	private final List<Paragraph> paragraphs;
	
	public Message(UserId senderId)
	{
		super();
		this.senderId = Objects.requireNonNull(senderId, "Sender id cannot be null");
		this.paragraphs = new ArrayList<Paragraph>();
	}

	public UserId getSenderId()
	{
		return senderId;
	}

	public List<Paragraph> getParagraphs()
	{
		return List.copyOf(paragraphs);
	}
	
	public void addParagraph(Paragraph paragraph)
	{
		if (paragraph == null)
		{
			throw new DomainException("Paragraph can't be null.");
		}
		
		this.paragraphs.add(paragraph);
		
	}
	
	public boolean ensureReadyToSend()
	{
		if (paragraphs.isEmpty())
		{
			throw new DomainException("Message must contain at least one paragraph");
		}
		
		return true;
	}
	
	
}
