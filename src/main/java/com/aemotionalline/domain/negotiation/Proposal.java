package com.aemotionalline.domain.negotiation;

import java.time.Instant;
import java.util.Objects;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.user.UserId;

/**
 * One version of what the partners are negotiating, with the author's justification for the edit.
 * The kind of content depends on the subclass ({@link AgreementProposal}, {@link DiscussionProposal}) and never changes.
 * Content, justification, status and sentAt change only through {@link Negotiation} (the setters are package-private),
 * so a proposal in the archive can't be rewritten from outside. sentAt is set when the proposal is sent.
 */
public abstract class Proposal 
{
	private final ProposalId id;
	private final UserId author;
	
    private ProposalContent content;
    private String justification;
    private Instant sentAt;
    
    private  ProposalStatus proposalStatus;

    protected Proposal 	
		    	(
				ProposalId id, 
				UserId author, 
				ProposalContent content
				) 
    {
    	this.id = Objects.requireNonNull(id);
        this.author = Objects.requireNonNull(author);
        this.content = Objects.requireNonNull(content);
        
        this.proposalStatus = ProposalStatus.DRAFT;
    }
 

	public UserId getAuthor() 
	{
		return author;
	}

	public ProposalContent getContent() 
	{
		return content;
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
	
	// A refused proposal is answered with a draft of the same kind, starting from the refused content.
	abstract Proposal draftFor(ProposalId newId, UserId newAuthor);
	
	// Each kind of proposal accepts only its own kind of content.
	abstract boolean acceptsContent(ProposalContent content);
	
	void ensureAcceptsContent(ProposalContent content)
	{
		if (content == null || !acceptsContent(content))
		{
			throw new DomainException("This kind of proposal doesn't accept this content.");
		}
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
	
	void setContent(ProposalContent content) 
	{
		ensureAcceptsContent(content);
		
		this.content = content;
	}


	public boolean isAccepted()
	{
		return this.proposalStatus == ProposalStatus.ACCEPTED;
	}
 
}
