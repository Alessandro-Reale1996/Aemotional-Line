package com.aemotionalline.domain.negotiation;

import java.time.Instant;
import java.util.Objects;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.user.UserId;

/**
 * One version of the agreement text, with the author's justification for the edit.
 * Text, justification, status and sentAt change only through {@link Negotiation} (the setters are package-private),
 * so a proposal in the archive can't be rewritten from outside. sentAt is set when the proposal is sent.
 */
public class Proposal 
{
	private final ProposalId id;
	private final UserId author;
	
    private String text;
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
        this.text = ensureValidText(text);
        
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
	
	
	// The agreement text can never be empty, both at creation and after an edit.
	static String ensureValidText(String text)
	{
		if (text == null || text.isBlank())
		{
			throw new DomainException("The proposal text cannot be blank.");
		}
		
		return text;
	}
	
	void setProposalStatus(ProposalStatus proposalStatus)
	{
		this.proposalStatus = Objects.requireNonNull(proposalStatus);
	}
	
	void setSentAt(Instant sentAt) 
	{
		this.sentAt = sentAt;
	}
	
	void setJustification(String justification) 
	{
		this.justification = justification;
	}
	
	void setText(String text) 
	{
		this.text = ensureValidText(text);
	}


	public boolean isAccepted()
	{
		return this.proposalStatus == ProposalStatus.ACCEPTED;
	}
 
}
