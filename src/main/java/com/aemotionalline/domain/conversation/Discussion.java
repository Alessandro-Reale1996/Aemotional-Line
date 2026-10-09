package com.aemotionalline.domain.conversation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.message.Message;
import com.aemotionalline.domain.message.MessageId;
import com.aemotionalline.domain.message.ParagraphId;
import com.aemotionalline.domain.negotiation.DiscussionTitle;
import com.aemotionalline.domain.user.UserId;

/**
 * An ordered exchange of messages inside a conversation. A specific discussion (a branch) keeps the title and subtitle
 * the partners settled on; the conversation's main discussion has none. It enforces turn-taking,
 * so the partners are forced to answer each other rather than write in bursts.
 * A discussion can be opened to expand a topic exposed in a paragraph (its origin): the paragraphs of its
 * first message then all refer to that paragraph, so a topic can continue across discussions.
 */
public class Discussion
{
	private final DiscussionId id;
	private final List<Message> messages;
	
	// When the recipient first read each message. Only the other partner's reads matter (the sender wrote it).
	private final Map<MessageId, Instant> readAt = new HashMap<>();
	
	private UserId lastSender;
	// Title and subtitle settled with a negotiation, or null for the main discussion.
	private final DiscussionTitle title;
	
	// The paragraph this discussion expands, or null when it doesn't start from one (e.g. the conversation's first).
	private final ParagraphId originParagraphId;
	
	public Discussion(DiscussionId id, DiscussionTitle title)
	{
		this(id, title, null);
	}
	
	public Discussion(DiscussionId id, DiscussionTitle title, ParagraphId originParagraphId)
	{
		this.id = id;
		this.title = title;
		this.originParagraphId = originParagraphId;
		
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
	
	public Optional<DiscussionTitle> getTitle() 
	{
		return Optional.ofNullable(title);
	}
	
	public Optional<ParagraphId> getOriginParagraphId()
	{
		return Optional.ofNullable(originParagraphId);
	}

	public Optional<Instant> getReadAt(MessageId messageId)
	{
		return Optional.ofNullable(readAt.get(messageId));
	}
	
	// Only the first read is kept: later reads of the same message don't move it.
	void markRead(MessageId messageId, Instant when)
	{
		readAt.putIfAbsent(messageId, when);
	}

	// Rejecting the same sender twice in a row is what makes the exchange turn-based.
	void setLastSender(UserId lastSender)
	{
		if (lastSender.equals(this.lastSender)) 
		{
			throw new DomainException("A new message can't be sent if an answer wasn't received.");
		}
		
		this.lastSender = lastSender;
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
	
	void addMessage(Message message)
	{
		if (message == null)
		{
			throw new DomainException("Message can't be null.");
		}
		
		message.ensureReadyToSend();
		this.messages.add(message);
	}
	
}
