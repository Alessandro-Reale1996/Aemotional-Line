package com.aemotionalline.domain.negotiation;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

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
	// The request to talk as the other partner received it: a frozen copy of the initial proposal, kept even if it is
	// never sent (a refused or withdrawn request). The initial proposal itself is the first draft and may still be edited.
	private final Proposal request;
	private final Instant startedAt;
	
	private NegotiationStatus negotiationStatus;
	// Who ended the negotiation without an agreement (refused the request or abandoned it), and when.
	private UserId closedBy;
	private Instant closedAt;
	
	private UserId currentResponder;
	private Proposal currentProposal;

	private Negotiation(NegotiationId id, Couple couple, Proposal initialProposal, UserId initialResponder, Clock clock) 
	{
		this(id, couple, clock, initialProposal.draftFor(initialProposal.getId(), initialProposal.getAuthor()), clock.instant());
		
		// Bound so that nobody can start another negotiation with it and change it.
		this.request.bindToNegotiation();
		this.currentProposal = initialProposal;
		this.currentResponder = initialResponder; 
		this.negotiationStatus = NegotiationStatus.DRAFT;
	}
	
	private Negotiation(NegotiationId id, Couple couple, Clock clock, Proposal request, Instant startedAt)
	{
		this.id = id;
		this.couple = couple;
		this.clock = clock;
		this.request = request;
		this.startedAt = startedAt;
		this.proposals = new ProposalArchive();
	}
	
	/**
	 * Reserved to the persistence layer: rebuilds a negotiation from its stored facts, without replaying it. Those facts
	 * were validated when they were produced, so no rule is checked again; the negotiation then goes on as before.
	 */
	public static Negotiation restore(NegotiationSnapshot snapshot, Clock clock)
	{
		if (snapshot == null || clock == null)
		{
			throw new DomainException("A snapshot and a clock are required to restore a negotiation.");
		}
		
		Negotiation negotiation = new Negotiation(snapshot.id(), snapshot.couple(), clock, snapshot.request(), snapshot.startedAt());
		
		negotiation.negotiationStatus = snapshot.status();
		negotiation.currentResponder = snapshot.currentResponder();
		negotiation.currentProposal = snapshot.currentProposal().orElse(null);
		negotiation.closedBy = snapshot.closedBy().orElse(null);
		negotiation.closedAt = snapshot.closedAt().orElse(null);
		snapshot.sentProposals().forEach(negotiation.proposals::add);
		
		return negotiation;
	}
	
	// The clock is injected so that the sentAt timestamps of the proposals are deterministic under test.
	// The initial proposal must be a fresh one: it is bound to this negotiation, last, once every check has passed.
	public static Negotiation start(NegotiationId id, Couple couple, Proposal initialProposal, Clock clock)
	{
		if (id == null || couple == null || initialProposal == null || clock == null)
		{
			throw new DomainException("An id, a couple, an initial proposal and a clock are required to start a negotiation.");
		}
		
		if (!couple.isPartner(initialProposal.getAuthor()))
		{
			throw new DomainException("Only a partner of the couple can start a negotiation.");
		}
		
		Negotiation negotiation = new Negotiation(id, couple, initialProposal, setFirstCurrentResponder(couple, initialProposal), clock);
		
		if(negotiation.currentResponder.equals(initialProposal.getAuthor()))
		{
			throw new DomainException("The author of the initial proposal can't be the initial responder.");
		}
		
		initialProposal.bindToNegotiation();
		
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

	public Proposal getRequest()
	{
		return request;
	}

	public Instant getStartedAt()
	{
		return startedAt;
	}

	public Optional<UserId> getClosedBy()
	{
		return Optional.ofNullable(closedBy);
	}

	public Optional<Instant> getClosedAt()
	{
		return Optional.ofNullable(closedAt);
	}

	/** Ended without an agreement: the request was refused or a partner abandoned. */
	public boolean isFailed()
	{
		return negotiationStatus == NegotiationStatus.REFUSED || negotiationStatus == NegotiationStatus.ABANDONED;
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
        closedBy = user;
        closedAt = clock.instant();
    }
    
    /**
     * Either partner may give up while the negotiation is open (the request is waiting, or proposals are being
     * exchanged), whoever's turn it is: nobody stays hostage to a negotiation the other doesn't carry on. The requester
     * withdrawing before the answer is an abandonment too. It is final; the draft in progress is never part of the
     * history, which only holds the proposals that were sent.
     */
    public void abandon(UserId user)
    {
    	ensureOpen(user);
    	
    	negotiationStatus = NegotiationStatus.ABANDONED;
    	closedBy = user;
    	closedAt = clock.instant();
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
    	
    	Proposal proposal = proposals.getLast().draftFor(idResponse, user);
    	proposal.bindToNegotiation();
    	
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
    
    public void editCurrentProposal(UserId user, ProposalContent content)
    {
    	ensureCanAct(user);
    	
    	ensureNegotiationStatus(NegotiationStatus.ACCEPTED);
    	
    	if(this.currentProposal == null)
    	{
    		throw new DomainException("There is not a proposal to edit.");
    	}
    	
    	if(!currentProposal.getAuthor().equals(user))
    	{
    		throw new DomainException("Only the author of the proposal can edit it.");
    	}
    	
    	this.currentProposal.setContent(content);
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
    // Terminal states (refused, abandoned, or last proposal accepted) are checked first: once reached, nobody can act again.
    private void ensureCanAct(UserId user) 
    {
        ensureOpen(user);

        if (!currentResponder.equals(user)) 
        {
            throw new DomainException("It is not this user's turn");
        }
    }
    
    // Everything but the turn: a partner acting on a negotiation that hasn't ended.
    private void ensureOpen(UserId user)
    {
        if (user == null)
        {
            throw new DomainException("A user is required to act on the negotiation.");
        }

        if ( negotiationStatus == NegotiationStatus.REFUSED) 
        {
            throw new DomainException("The negotiation was refused.");
        }
        
        if (negotiationStatus == NegotiationStatus.ABANDONED)
        {
            throw new DomainException("The negotiation was abandoned.");
        }
        
        if (!proposals.isEmpty() && proposals.getLast().getProposalStatus() == ProposalStatus.ACCEPTED)
        {
        	throw new DomainException("The negotiation is concluded: the last proposal was accepted.");
        }

        if (!this.couple.isPartner(user)) 
        {
            throw new DomainException("User does not belong to the couple");
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
