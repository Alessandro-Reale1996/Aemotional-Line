package com.aemotionalline.domain.negotiation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.user.UserId;

public class Negotiation 
{	
	private final NegotiationId id;
	private final Couple couple;
	private final List<Proposal> proposals;
	
	private NegotiationStatus negotiationStatus;
	private UserId currentResponder;

	private Negotiation(NegotiationId id, Couple couple, NegotiationStatus negotiationStatus) 
	{
		super();
		this.id = id;
		this.couple = couple;
		this.proposals = new ArrayList<Proposal>();
		this.negotiationStatus = negotiationStatus;
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
    
    public void modify(
            UserId user,
            String text,
            String justification
    ) {
        ensureCanRespond(user);

        Proposal newProposal =
                getCurrentProposal().revise(
                        user,
                        text,
                        justification
                );

        proposals.add(newProposal);

        currentResponder = getOtherPartner(user);
    }
    
    public void accept(UserId user) 
    {

        ensureCanRespond(user);

        negotiationStatus = NegotiationStatus.ACCEPTED;
    }
	
    public boolean isAccepted() 
    {
        return negotiationStatus == NegotiationStatus.ACCEPTED;
    }

    private void ensureCanRespond(UserId user) 
    {

        Objects.requireNonNull(user);

        if (negotiationStatus != NegotiationStatus.WAITING_FOR_RESPONSE) 
        {
            throw new IllegalStateException( "Negotiation is not waiting for a response");
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
}
