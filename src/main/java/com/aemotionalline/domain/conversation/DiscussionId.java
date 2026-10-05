package com.aemotionalline.domain.conversation;

import java.util.Objects;

/**
 * A discussion is only unique within its conversation, hence the composite identity. The number is the
 * discussion's position in the conversation (0, 1, 2...), which people read, so it stays a plain counter.
 */
public record DiscussionId(ConversationId conversationId, int discussionNumber)
{
	public DiscussionId
	{	
		Objects.requireNonNull(conversationId, "Conversation ID cannot be null.");

        if (discussionNumber < 0) 
        {
            throw new IllegalArgumentException("Discussion number cannot be negative.");
        }
	}
	
}
