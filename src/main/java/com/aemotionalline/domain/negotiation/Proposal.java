package com.aemotionalline.domain.negotiation;

import java.time.Instant;
import java.util.Objects;

import com.aemotionalline.domain.user.UserId;

/**
 * One version of the agreement text, with the author's justification for the edit.
 * The clock is injected at creation so that timestamps are deterministic under test.
 */
public class Proposal 
{
	private final ProposalId id;
	private final UserId author;
    private final String text;
    private String justification;
    private Instant sentAt;
    
    private  ProposalStatus proposalStatus;

    public Proposal 	
		    	(
				ProposalId id, 
				UserId author, 
				String text
				) 
    {
    	this.id = Objects.requireNonNull(id);
        this.author = Objects.requireNonNull(author);
        this.text = Objects.requireNonNull(text);
        
        this.proposalStatus = ProposalStatus.DRAFT;
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
		this.proposalStatus = Objects.requireNonNull(proposalStatus);
	}
	
	public void setSentAt(Instant sentAt) 
	{
		this.sentAt = sentAt;
	}
	
	public void setJustification(String justification) 
	{
		this.justification = justification;
	}


	public boolean isAccepted()
	{
		return this.proposalStatus == ProposalStatus.ACCEPTED;
	}
 
}
