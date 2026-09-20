package com.aemotionalline.domain.conversation;

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
            throw new IllegalArgumentException("Negotiation number cannot be negative.");
        }
	}
	
}
