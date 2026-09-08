package com.aemotionalline.domain.negotiation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.user.UserId;

public class Negotiation 
{	
	private final NegotiationId id;
	private final Couple couple;
	private final List<Proposal> proposals;
	
	private NegotiationStatus negotiationStatus;
	
	private UserId currentResponder;
	private Proposal currentProposal;

	private Negotiation(NegotiationId id, Couple couple, Proposal initialProposal, UserId currentResponder) 
	{
		super();
		this.id = id;
		this.couple = couple;
		this.currentResponder = currentResponder; 
		this.currentProposal = initialProposal;
		this.proposals = new ArrayList<Proposal>();
		this.negotiationStatus = NegotiationStatus.DRAFT;
		
		
	}

	public NegotiationId getId() {
		return id;
	}

	public Couple getCouple() {
		return couple;
	}

	public List<Proposal> getProposals() {
		return List.copyOf(proposals);
	}

	public NegotiationStatus getNegotiationStatus() {
		return negotiationStatus;
	}
	

	public static Negotiation start
    		(
    		NegotiationId negotiationId, 
    		Couple couple, 
    		Proposal initialProposal, 
    		UserId currentResponder
    		) 
    {
    	Objects.requireNonNull(negotiationId);
        Objects.requireNonNull(couple);
        Objects.requireNonNull(initialProposal);
        Objects.requireNonNull(currentResponder);

        if (!couple.isPartner(initialProposal.getAuthor())) 
        {
            throw new IllegalArgumentException("Proposal author does not belong to the couple");
        }

        if (!couple.isPartner(currentResponder)) 
        {
            throw new IllegalArgumentException("Responder does not belong to the couple");
        }

        if (initialProposal.getAuthor().equals(currentResponder)) 
        {
            throw new IllegalArgumentException("The author of the initial proposal cannot be its responder");
        }

        return new Negotiation (negotiationId, couple, initialProposal, currentResponder);
    }
    
    
    public void acceptNegotiation(UserId user) 
    {

        ensureCanRespond(user);

        negotiationStatus = NegotiationStatus.ACCEPTED;
    }
	
    public void refuseNegotiation(UserId user) 
    {

        ensureCanRespond(user);

        negotiationStatus = NegotiationStatus.REFUSED;
    }
    
    public void acceptProposal()
    {
    	proposals.getLast().setProposalStatus(ProposalStatus.ACCEPTED);
    }
    
    public void refuseProposal()
    {
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
    	ensureCanRespond(user);
    	
    	currentProposal.setProposalStatus(ProposalStatus.WAITING_FOR_RESPONSE);
    	
    	proposals.add(currentProposal);
    	
    	if(currentResponder.equals(couple.getPartnerOneId()))
    	{
    		currentResponder = couple.getPartnerTwoId();
    	}
    	else if (currentResponder.equals(couple.getPartnerTwoId()))
    	{
    		currentResponder = couple.getPartnerOneId();
    	}

    }

    
    private void ensureCanRespond(UserId user) 
    {

        Objects.requireNonNull(user);

        if ( negotiationStatus == NegotiationStatus.REFUSED) 
        {
            throw new IllegalStateException("Negotiation is refused");
        }
        
        if (proposals.getLast().getProposalStatus() == ProposalStatus.ACCEPTED)
        {
        	throw new DomainException("User can't keep sending proposal if the last was accepted.");
        }

        if (!this.couple.isPartner(user)) 
        {
            throw new IllegalArgumentException("User does not belong to the couple");
        }

        if (!currentResponder.equals(user)) 
        {
            throw new IllegalStateException("It is not this user's turn");
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
