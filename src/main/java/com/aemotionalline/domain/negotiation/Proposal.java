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
    
    // Set once by the negotiation that takes the proposal: a proposal belongs to one negotiation for its whole life.
    private boolean bound;

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
 

	/**
	 * Reserved to the persistence layer: rebuilds a proposal from its stored facts, without going through a negotiation.
	 * The kind (agreement or discussion title) follows the content. The proposal belongs to the negotiation or record
	 * it is restored into, so it can't start another negotiation.
	 */
	public static Proposal restore(ProposalId id, UserId author, ProposalContent content, String justification, Instant sentAt, ProposalStatus status)
	{
		if (content == null || status == null)
		{
			throw new DomainException("A proposal needs its content and status.");
		}
		
		Proposal proposal = switch (content)
		{
			case AgreementText text -> new AgreementProposal(id, author, text.text());
			case DiscussionTitle title -> new DiscussionProposal(id, author, title.title(), title.subtitle());
		};
		
		proposal.justification = justification;
		proposal.sentAt = sentAt;
		proposal.proposalStatus = status;
		proposal.bound = true;
		
		return proposal;
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
	
	// Only a fresh proposal can join a negotiation: one already taken (a draft of another negotiation, or a proposal it
	// sent) would be shared, and the new negotiation would rewrite the other's history when it sends it.
	void bindToNegotiation()
	{
		if (bound)
		{
			throw new DomainException("The proposal already belongs to a negotiation.");
		}
		
		bound = true;
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
