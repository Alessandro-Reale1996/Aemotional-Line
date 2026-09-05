package com.aemotionalline.domain.negotiation;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;

import com.aemotionalline.domain.user.UserId;

public class Proposal 
{
	private final ProposalId id;
	private final UserId author;
    private final String text;
    private final String justification;
    private final Instant sentAt;
    
    private  ProposalStatus proposalStatus;

    private Proposal (ProposalId id, UserId author, String text, String justification, Instant sentAt, ProposalStatus proposalStatus) 
    {
    	this.id = id;
        this.author = Objects.requireNonNull(author);
        this.text = Objects.requireNonNull(text);
        this.justification = justification;
        this.sentAt = Objects.requireNonNull(sentAt); 
        
        this.proposalStatus = ProposalStatus.DRAFT;
    }
    
    public static Proposal create( ProposalId proposalId, UserId author, String text, String justification, Clock clock, ProposalStatus proposalStatus) 
    {
        return new Proposal(
            proposalId,
            author,
            text,
            justification,
            Instant.now(clock),
            proposalStatus
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

	public ProposalStatus getProposalStatus() 
	{
		return proposalStatus;
	}


	public ProposalId getId() 
	{
		return id;
	}

	public Instant getSentAt() 
	{
		return sentAt;
	}
	
	public void setProposalStatus(ProposalStatus proposalStatus)
	{
		this.proposalStatus = proposalStatus;
	}
 
}
