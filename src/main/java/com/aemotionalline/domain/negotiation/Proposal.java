package com.aemotionalline.domain.negotiation;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;

import com.aemotionalline.domain.user.UserId;

public final class Proposal 
{
	private final ProposalId id;
	private final UserId author;
    private final String text;
    private final String justification;
    private final Instant sentAt;

    private Proposal (ProposalId id, UserId author, String text, String justification, Instant sentAt) 
    {
    	this.id = id;
        this.author = Objects.requireNonNull(author);
        this.text = Objects.requireNonNull(text);
        this.justification = justification;
        this.sentAt = Objects.requireNonNull(sentAt); 
    }
    
    public static Proposal create( ProposalId proposalId, UserId author, String text, String justification, Clock clock) 
    {
        return new Proposal(
            proposalId,
            author,
            text,
            justification,
            Instant.now(clock)
        );
    }

	public UserId getAuthor() 
	{
		return author;
	}

	public String getText() 
	{
		return text;
	}

	public String getJustification() 
	{
		return justification;
	}
 
}
