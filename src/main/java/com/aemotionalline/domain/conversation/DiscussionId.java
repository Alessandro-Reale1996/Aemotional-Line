package com.aemotionalline.domain.conversation;

import java.util.Objects;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.common.ErrorCode;

/**
 * A discussion is only unique within its conversation, hence the composite identity. The number is the
 * discussion's position in the conversation (0, 1, 2...), which people read, so it stays a plain counter.
 */
public record DiscussionId(ConversationId conversationId, int discussionNumber)
{
	public DiscussionId
	{	
		Objects.requireNonNull(conversationId, "Conversation ID cannot be null.");

        // No discussion has such a number: it is the answer to asking for one, not a broken rule.
        if (discussionNumber < 0) 
        {
            throw new DomainException(ErrorCode.NOT_FOUND, "Discussion not found.");
        }
	}
	
}
