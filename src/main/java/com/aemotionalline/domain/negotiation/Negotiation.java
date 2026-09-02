package com.aemotionalline.domain.negotiation;

import java.util.HashSet;
import java.util.Set;

import com.aemotionalline.domain.couple.Couple;

public class Negotiation 
{	
	private final NegotiationId negotiationId;
	private final Couple couple;
	private final Set<Proposal> proposals;
	
	private NegotiationStatus negotiationStatus;

	public Negotiation(NegotiationId id, Couple couple, NegotiationStatus negotiationStatus) 
	{
		super();
		this.negotiationId = id;
		this.couple = couple;
		this.proposals = new HashSet<Proposal>();
		this.negotiationStatus = negotiationStatus;
	}
	
	
	
	
}
