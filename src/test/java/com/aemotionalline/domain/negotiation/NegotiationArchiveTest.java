package com.aemotionalline.domain.negotiation;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.user.UserId;

public class NegotiationArchiveTest 
{
	@Test
	void shouldNotAddAlredyPresentNegotiation()
	{
		NegotiationArchive archive = new NegotiationArchive();
		
		Couple couple = new Couple(10L, new UserId(1L), new UserId(2L), new UserId(3L));
		
		Proposal proposal = Proposal.create(new ProposalId(0L), couple.getPartnerOneId(), "TEST TEXT", "TEST JUSTIFICATION", Clock.systemUTC(), ProposalStatus.DRAFT);
		
		Negotiation negotiation = Negotiation.start(new NegotiationId(0L), couple, proposal, couple.getPartnerTwoId());
		
		archive.add(negotiation);
		
		assertThrows(DomainException.class, ()-> archive.add(negotiation));
	
	}
	
	@Test
	void shouldNotAddSameIdNegotiation()
	{
		NegotiationArchive archive = new NegotiationArchive();
		
		Couple couple = new Couple(10L, new UserId(1L), new UserId(2L), new UserId(3L));
		
		Proposal proposal = Proposal.create(new ProposalId(0L), couple.getPartnerOneId(), "TEST TEXT", "TEST JUSTIFICATION", Clock.systemUTC(), ProposalStatus.DRAFT);
		
		Negotiation negotiation = Negotiation.start(new NegotiationId(0L), couple, proposal, couple.getPartnerTwoId());
		
		Negotiation negotiationSameId = Negotiation.start(new NegotiationId(0L), couple, proposal, couple.getPartnerTwoId());
		
		archive.add(negotiation);
		
		assertThrows(DomainException.class, ()-> archive.add(negotiationSameId));
	}
	
}