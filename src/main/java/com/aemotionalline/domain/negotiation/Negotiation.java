package com.aemotionalline.domain.negotiation;

import java.util.Objects;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.user.UserId;

/**
 * The "stipula" of the agreement: the partners alternate turns exchanging {@link Proposal}s until one is accepted.
 * Two statuses are tracked independently: {@link NegotiationStatus} says whether the request to talk was accepted,
 * while {@link ProposalStatus} says whether the agreement text itself was accepted.
 */
public class Negotiation 
{	
	private final NegotiationId id;
	private final Couple couple;
	private final ProposalArchive proposals;
	
	private NegotiationStatus negotiationStatus;
	
	private UserId currentResponder;
	private Proposal currentProposal;

	private Negotiation(NegotiationId id, Couple couple, Proposal initialProposal, UserId initialResponder) 
	{
		super();
		this.id = id;
		this.couple = couple;
		this.currentProposal = initialProposal;
		
		this.currentResponder = initialResponder; 
		this.proposals = new ProposalArchive();
		this.negotiationStatus = NegotiationStatus.DRAFT;
	}
	
	public static Negotiation start(NegotiationId id, Couple couple, Proposal initialProposal)
	{
		Objects.requireNonNull(id);
		Objects.requireNonNull(couple);
		Objects.requireNonNull(initialProposal);
		
		
		Negotiation negotiation = new Negotiation(id, couple, initialProposal, setFirstCurrentResponder(couple, initialProposal));
		
		if(negotiation.currentResponder.equals(initialProposal.getAuthor()))
		{
			throw new DomainException("The author of the initial proposal can't be the initial responder.");
		}
		
		return negotiation;
	}

	public NegotiationId getId() 
	{
		return id;
	}

	public Couple getCouple() 
	{
		return couple;
	}

	public ProposalArchive getProposals() 
	{
		return proposals;
	}

	public NegotiationStatus getNegotiationStatus() 
	{
		return negotiationStatus;
	}
	
	public UserId getCurrentResponder() 
	{
		return currentResponder;
	}

	public Proposal getCurrentProposal() 
	{
		return currentProposal;
	}

    
    // Accepting the request to talk hands the turn back to the author, who must now send the first proposal.
    public void acceptNegotiation(UserId user) 
    {

    	ensureCanAct(user);

        negotiationStatus = NegotiationStatus.ACCEPTED;
        
        switchCurrentResponder();
    }
	
    public void refuseNegotiation(UserId user) 
    {

    	ensureCanAct(user);

        negotiationStatus = NegotiationStatus.REFUSED;
    }
    
    public void acceptProposal(UserId user)
    {
    	ensureCanAct(user);
    	
    	proposals.getLast().setProposalStatus(ProposalStatus.ACCEPTED);
    }
    
    public void refuseProposal(UserId user)
    {
    	ensureCanAct(user);
    	
    	proposals.getLast().setProposalStatus(ProposalStatus.REFUSED);
    }
    
    public boolean isAccepted() 
    {
        return negotiationStatus == NegotiationStatus.ACCEPTED;
    }

    
    public  Proposal getLastProposal()
    {
    	return proposals.getLast();
    }
    
    public void setCurrentProposal(Proposal proposal)
    {
    	Objects.requireNonNull(proposal);	
    	
    	this.currentProposal = proposal;
    }
    
    public void sendProposal(UserId user)
    {
    	
    	ensureCanAct(user);
    	
    	currentProposal.setProposalStatus(ProposalStatus.WAITING_FOR_RESPONSE);
    	
    	proposals.add(currentProposal);
    	
    	switchCurrentResponder();

    }

    
    // Terminal states (refused, or last proposal accepted) throw IllegalStateException because no user could ever act again;
    // membership and turn violations throw DomainException because a different user could still act.
    private void ensureCanAct(UserId user) 
    {

        Objects.requireNonNull(user);

        if ( negotiationStatus == NegotiationStatus.REFUSED) 
        {
            throw new IllegalStateException("Negotiation is refused");
        }
        
        if (!proposals.isEmpty() && proposals.getLast().getProposalStatus() == ProposalStatus.ACCEPTED)
        {
        	throw new IllegalStateException("User can't keep sending proposal if the last was accepted.");
        }

        if (!this.couple.isPartner(user)) 
        {
            throw new DomainException("User does not belong to the couple");
        }

        if (!currentResponder.equals(user)) 
        {
            throw new DomainException("It is not this user's turn");
        }
    }	
    
    private void switchCurrentResponder()
    {
    	if(currentResponder.equals(this.couple.getPartnerOneId()))
    	{
    		currentResponder = this.couple.getPartnerTwoId();
    	}
    	else if (currentResponder.equals(this.couple.getPartnerTwoId()))
    	{
    		currentResponder = this.couple.getPartnerOneId();
    	}
    }
    
    // The partner who did not write the initial proposal answers first, so an author can never respond to their own proposal.
    private  static UserId setFirstCurrentResponder(Couple couple, Proposal initialProposal)
    {
    	if(couple.getPartnerOneId().equals(initialProposal.getAuthor()))
    	{
    		return couple.getPartnerTwoId();
    	}
    	else
    	{
    		return couple.getPartnerOneId();
    	}
    }
    
    @Override
    public boolean equals(Object o) 
    {
        if (this == o) 
        {
            return true;
        }

        if (!(o instanceof Negotiation other)) 
        {
            return false;
        }

        return id.equals(other.id);
    }

    @Override
    public int hashCode() 
    {
        return id.hashCode();
    }
    
   
}
