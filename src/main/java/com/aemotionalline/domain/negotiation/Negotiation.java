package com.aemotionalline.domain.negotiation;

import java.time.Clock;
import java.util.List;
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
	private final Clock clock;
	
	private NegotiationStatus negotiationStatus;
	
	private UserId currentResponder;
	private Proposal currentProposal;

	private Negotiation(NegotiationId id, Couple couple, Proposal initialProposal, UserId initialResponder, Clock clock) 
	{
		super();
		this.id = id;
		this.couple = couple;
		this.clock = clock;
		this.currentProposal = initialProposal;
		
		this.currentResponder = initialResponder; 
		this.proposals = new ProposalArchive();
		this.negotiationStatus = NegotiationStatus.DRAFT;
	}
	
	// The clock is injected so that the sentAt timestamps of the proposals are deterministic under test.
	public static Negotiation start(NegotiationId id, Couple couple, Proposal initialProposal, Clock clock)
	{
		Objects.requireNonNull(id);
		Objects.requireNonNull(couple);
		Objects.requireNonNull(initialProposal);
		Objects.requireNonNull(clock);
		
		if (!couple.isPartner(initialProposal.getAuthor()))
		{
			throw new DomainException("Only a partner of the couple can start a negotiation.");
		}
		
		Negotiation negotiation = new Negotiation(id, couple, initialProposal, setFirstCurrentResponder(couple, initialProposal), clock);
		
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

	public List<Proposal> getProposals()
	{
		return proposals.getProposals();
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
    	
    	ensureNegotiationStatus(NegotiationStatus.DRAFT);

        negotiationStatus = NegotiationStatus.ACCEPTED;
        
        switchCurrentResponder();
    }
	
    public void refuseNegotiation(UserId user) 
    {
    	ensureCanAct(user);
    	
    	ensureNegotiationStatus(NegotiationStatus.DRAFT);

        negotiationStatus = NegotiationStatus.REFUSED;
    }
    
    public void acceptProposal(UserId user)
    {
    	ensureCanAct(user);
    	
    	ensureNegotiationStatus(NegotiationStatus.ACCEPTED);
    	
    	ensureProposalAwaitsResponse();
    	
    	proposals.getLast().setProposalStatus(ProposalStatus.ACCEPTED);
    }
    
    // Refusing means entering edit mode: the refuser gets a new draft, with its own id, starting from the refused text.
    // Every check runs before the state changes, so a rejected call never leaves the negotiation half-refused.
    public void refuseProposal(UserId user, ProposalId idResponse)
    {
    	ensureCanAct(user);
    	
    	ensureNegotiationStatus(NegotiationStatus.ACCEPTED);
    	
    	ensureProposalAwaitsResponse();
    	
    	if (idResponse == null)
    	{
    		throw new DomainException("The id of the counter-proposal is required.");
    	}
    	
    	ensureProposalIdNotAlreadyPresent(idResponse);
    	
    	proposals.getLast().setProposalStatus(ProposalStatus.REFUSED);
    	
    	Proposal proposal = new Proposal(idResponse, user, proposals.getLast().getText());
    	
    	this.currentProposal = proposal;
    }
    
    public boolean isAccepted() 
    {
        return negotiationStatus == NegotiationStatus.ACCEPTED;
    }

    
    public  Proposal getLastProposal()
    {
    	if(this.proposals.isEmpty())
    	{
    		throw new DomainException("No proposal has been sent yet.");
    	}
    	
    	return proposals.getLast();
    }
    
    public void editCurrentProposal(UserId user, String text)
    {
    	ensureCanAct(user);
    	
    	ensureNegotiationStatus(NegotiationStatus.ACCEPTED);
    	
    	Proposal.ensureValidText(text);
    
    	if(this.currentProposal == null)
    	{
    		throw new DomainException("There is not a proposal to edit.");
    	}
    	
    	if(!currentProposal.getAuthor().equals(user))
    	{
    		throw new DomainException("Only the author of the proposal can edit it.");
    	}
    	
    	this.currentProposal.setText(text);
    }
    
    public void sendProposal(UserId user, String justification)
    {
    	ensureCanAct(user);
    	
    	ensureNegotiationStatus(NegotiationStatus.ACCEPTED);
    	
    	if(this.currentProposal == null)
    	{
    		throw new DomainException("There is not a proposal to send.");
    	}
    	
    	currentProposal.setProposalStatus(ProposalStatus.WAITING_FOR_RESPONSE);
    	
    	currentProposal.setJustification(justification);
    	
    	currentProposal.setSentAt(clock.instant());
    	
    	proposals.add(currentProposal);
    	
    	this.currentProposal = null;
    	
    	switchCurrentResponder();

    }
    
    // Every rule violation throws DomainException, so the REST layer can map them all to one client error.
    // Terminal states (refused, or last proposal accepted) are checked first: once reached, nobody can act again.
    private void ensureCanAct(UserId user) 
    {
        if (user == null)
        {
            throw new DomainException("A user is required to act on the negotiation.");
        }

        if ( negotiationStatus == NegotiationStatus.REFUSED) 
        {
            throw new DomainException("The negotiation was refused.");
        }
        
        if (!proposals.isEmpty() && proposals.getLast().getProposalStatus() == ProposalStatus.ACCEPTED)
        {
        	throw new DomainException("The negotiation is concluded: the last proposal was accepted.");
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
    
    // getLastProposal() already rejects an empty archive, so only the status needs checking here.
    private void ensureProposalAwaitsResponse()
    {
    	if (getLastProposal().getProposalStatus() != ProposalStatus.WAITING_FOR_RESPONSE)
    	{
    		throw new DomainException("The last proposal is not waiting for a response.");
    	}
    }
    
    private void ensureNegotiationStatus(NegotiationStatus expected)
    {
    	if(this.negotiationStatus != expected)
    	{
    		throw new DomainException("The current negotiation status doesn't allow this operation: " + expected + " was expected, " + negotiationStatus + " was found.");
    	}
    }
    
    private void ensureProposalIdNotAlreadyPresent(ProposalId proposalId)
    {
    	for (Proposal proposal : this.proposals.getProposals())
    	{
    		if(proposal.getId().equals(proposalId))
    		{
    			throw new DomainException("A proposal with this id is already in the archive.");
    		}
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
