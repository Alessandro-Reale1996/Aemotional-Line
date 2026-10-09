package com.aemotionalline.domain.negotiation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.Ids;

public class NegotiationArchiveTest 
{
	@Test
	void shouldNotAddAlreadyPresentNegotiation()
	{
		NegotiationArchive archive = new NegotiationArchive();
		
		Couple couple = new Couple(Ids.couple(10L), Ids.user(1L), Ids.user(2L), Ids.user(3L));
		
		Proposal proposal = new AgreementProposal(Ids.proposal(0L), couple.getPartnerOneId(), "TEST TEXT");
		
		Negotiation negotiation = Negotiation.start(Ids.negotiation(0L), couple, proposal, Clock.systemUTC());
		
		archive.add(negotiation);
		
		assertThrows(DomainException.class, ()-> archive.add(negotiation));
	
	}
	
	@Test
	void shouldNotAddSameIdNegotiation()
	{
		NegotiationArchive archive = new NegotiationArchive();
		
		Couple couple = new Couple(Ids.couple(10L), Ids.user(1L), Ids.user(2L), Ids.user(3L));
		
		Proposal proposal = new AgreementProposal(Ids.proposal(0L), couple.getPartnerOneId(), "TEST TEXT");
		
		Negotiation negotiation = Negotiation.start(Ids.negotiation(0L), couple, proposal, Clock.systemUTC());
		
		Negotiation negotiationSameId = Negotiation.start(Ids.negotiation(0L), couple, proposal, Clock.systemUTC());
		
		archive.add(negotiation);
		
		assertThrows(DomainException.class, ()-> archive.add(negotiationSameId));
	}
	
	@Test
	void shouldFindRightID()
	{
		var archive = new NegotiationArchive();
		
		Couple couple = new Couple(Ids.couple(10L), Ids.user(1L), Ids.user(2L), Ids.user(3L));
		
		Proposal proposal = new AgreementProposal(Ids.proposal(0L), couple.getPartnerOneId(), "TEST TEXT");
		
		Negotiation negotiation0 = Negotiation.start(Ids.negotiation(0L), couple, proposal, Clock.systemUTC());
		
		Negotiation negotiation1 = Negotiation.start(Ids.negotiation(1L), couple, proposal, Clock.systemUTC());
		
		
		archive.add(negotiation0);
		archive.add(negotiation1);	
		assertEquals(negotiation0, archive.findById(Ids.negotiation(0L)));
		assertEquals(negotiation1, archive.findById(Ids.negotiation(1L)));
		
	}
	
	@Test
	void shouldThrowExceptionWhenIDNotFound()
	{
		NegotiationArchive archive = new NegotiationArchive();
		
		assertThrows(DomainException.class, () -> archive.findById(Ids.negotiation(1L)));
	}
	
	
}