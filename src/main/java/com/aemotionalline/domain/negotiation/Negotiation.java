package com.aemotionalline.domain.negotiation;

import java.util.Objects;

import org.springframework.boot.webmvc.autoconfigure.WebMvcProperties.Apiversion.Use;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.user.UserId;

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
