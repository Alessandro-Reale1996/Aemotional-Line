package com.aemotionalline.domain.conversation;

/** A discussion is only unique within its conversation, hence the composite identity. */
public record DiscussionId(long conversationId, long discussionNumber)
{
	public DiscussionId
	{	
		if (conversationId < 0) 
        {
            throw new IllegalArgumentException("Conversation ID cannot be negative.");
        }

        if (discussionNumber < 0) 
        {
            throw new IllegalArgumentException("Discussion number cannot be negative.");
        }
	}
	
}
