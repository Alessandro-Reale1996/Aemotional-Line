package com.aemotionalline.domain.negotiation;

public record NegotiationId(long conversationId, long negotiationNumber) 
{
	public NegotiationId 
	{
        if (conversationId < 0) 
        {
            throw new IllegalArgumentException("Conversation ID cannot be negative.");
        }

        if (negotiationNumber < 0) 
        {
            throw new IllegalArgumentException("Negotiation number cannot be negative.");
        }
    }
}
