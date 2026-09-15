package com.aemotionalline.domain.negotiation;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;


import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.user.UserId;

public class NegotiationTest 
{
	@Test
	public void shouldThrowExceptionWhenAuthorOfInitialProposalIsRepsonder()
	{
		Couple couple = new Couple(1L, new UserId(10L), new UserId(20L), new UserId(30L));
		
		Proposal proposal = Proposal.create(new ProposalId(0L), couple.getPartnerOneId(), "TEXT", "JUSTIFICATION TEXT", Clock.systemDefaultZone(), ProposalStatus.DRAFT);
		
		assertThrows(IllegalArgumentException.class, () -> Negotiation.start(new NegotiationId(100L), couple, proposal, couple.getPartnerOneId()));
	}
	
	// TESTING ensureCanRespond() METHOD USING acceptNegotiation():
	
	
	
	
}
