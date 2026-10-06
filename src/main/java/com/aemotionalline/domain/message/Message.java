package com.aemotionalline.domain.message;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.user.UserId;

/**
 * What one partner sends in a single turn: an ordered list of paragraphs and nothing else
 * (no attachments), so the whole content of the exchange stays readable and analysable as text.
 */
public class Message
{
	private final MessageId id;
	private final UserId senderId;
	private final List<Paragraph> paragraphs;
	
	private boolean sealed;
	
	// The questions of the answered message that this message left without an answer, recorded when it is sent
	// (spec 1.3.3: the user may skip a question, but the choice is tracked). Final once the message is sealed.
	private List<ParagraphId> questionsLeftUnanswered = List.of();
	
	public Message(MessageId id, UserId senderId)
	{
		super();
		this.id = Objects.requireNonNull(id);
		this.senderId = Objects.requireNonNull(senderId, "Sender id cannot be null");
		this.paragraphs = new ArrayList<Paragraph>();
	}
	
	

	public MessageId getId()
	{
		return id;
	}

	public UserId getSenderId()
	{
		return senderId;
	}

	public List<Paragraph> getParagraphs()
	{
		return List.copyOf(paragraphs);
	}
	
	public boolean isSealed()
	{
		return sealed;
	}
	
	public void addParagraph(Paragraph paragraph)
	{
		if (sealed)
		{
			throw new DomainException("A sent message can't be changed.");
		}
		
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
	
	/**
	 * Called when the message is sent: the message and its paragraphs become read-only, so the history
	 * can't be rewritten. As references may only point to paragraphs already sent (and therefore sealed),
	 * no new link can ever close a cycle.
	 */
	public List<ParagraphId> getQuestionsLeftUnanswered()
	{
		return questionsLeftUnanswered;
	}
	
	public void seal()
	{
		seal(List.of());
	}
	
	// Seals the message and records, in the same step, the questions it left unanswered: the record can't change later.
	public void seal(List<ParagraphId> questionsLeftUnanswered)
	{
		if (sealed)
		{
			throw new DomainException("The message is already sealed.");
		}
		
		if (questionsLeftUnanswered == null || questionsLeftUnanswered.stream().anyMatch(Objects::isNull))
		{
			throw new DomainException("The questions left unanswered can't be null.");
		}
		
		this.questionsLeftUnanswered = List.copyOf(questionsLeftUnanswered);
		this.sealed = true;
		
		for (Paragraph paragraph : paragraphs)
		{
			paragraph.seal();
		}
	}
	
	
}
