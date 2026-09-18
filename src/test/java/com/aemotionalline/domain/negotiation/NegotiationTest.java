package com.aemotionalline.domain.negotiation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;


import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.common.DomainException;
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
	
	@Test
	void shouldThrowExceptionIfNegotiationStatusIsRefused()
	{
		Couple couple = new Couple(1L, new UserId(10L), new UserId(20L), new UserId(30L));
		
		Proposal proposal = Proposal.create(new ProposalId(0L), couple.getPartnerOneId(), "TEXT", "JUSTIFICATION TEXT", Clock.systemDefaultZone(), ProposalStatus.DRAFT);
		
		Negotiation negotiation = Negotiation.start(new NegotiationId(100L), couple, proposal, couple.getPartnerTwoId());
		
		negotiation.refuseNegotiation(couple.getPartnerTwoId());
		
		assertThrows(IllegalStateException.class, ()-> negotiation.acceptNegotiation(couple.getPartnerTwoId()));
		
	}
	
	@Test
	void shouldThrowExceptionIfProposalStatusIsAccepted()
	{
		Couple couple = new Couple(1L, new UserId(10L), new UserId(20L), new UserId(30L));
		
		Proposal proposal = Proposal.create(new ProposalId(0L), couple.getPartnerOneId(), "TEXT", "JUSTIFICATION TEXT", Clock.systemDefaultZone(), ProposalStatus.DRAFT);
		
		Negotiation negotiation = Negotiation.start(new NegotiationId(100L), couple, proposal, couple.getPartnerTwoId());
		
		negotiation.acceptNegotiation(couple.getPartnerTwoId());
		
		negotiation.sendProposal(couple.getPartnerOneId());
		
		negotiation.acceptProposal(couple.getPartnerTwoId());
		
		assertThrows(IllegalStateException.class, ()-> negotiation.acceptNegotiation(couple.getPartnerTwoId()));
		
	}
	
	@Test
	void shouldThrowExceptionIfUserIsNotInTheCouple()
	{
		Couple couple = new Couple(1L, new UserId(10L), new UserId(20L), new UserId(30L));
		
		Proposal proposal = Proposal.create(new ProposalId(0L), couple.getPartnerOneId(), "TEXT", "JUSTIFICATION TEXT", Clock.systemDefaultZone(), ProposalStatus.DRAFT);
		
		Negotiation negotiation = Negotiation.start(new NegotiationId(100L), couple, proposal, couple.getPartnerTwoId());
		
		assertThrows(DomainException.class, ()-> negotiation.acceptNegotiation(new UserId(123L)));
	}
	
	@Test
	void shouldThrowExceptionIfUserIsNotTheCurrentResponder()
	{
		Couple couple = new Couple(1L, new UserId(10L), new UserId(20L), new UserId(30L));
		
		Proposal proposal = Proposal.create(new ProposalId(0L), couple.getPartnerOneId(), "TEXT", "JUSTIFICATION TEXT", Clock.systemDefaultZone(), ProposalStatus.DRAFT);
		
		Negotiation negotiation = Negotiation.start(new NegotiationId(100L), couple, proposal, couple.getPartnerTwoId());
		
		assertThrows(DomainException.class, ()-> negotiation.acceptNegotiation(couple.getPartnerOneId()));
	}
	
	// 
	
	@Test 
	void shouldSwitchCurrentResponderAfterAnActionOfTheCorresponder()
	{
		Couple couple = new Couple(1L, new UserId(10L), new UserId(20L), new UserId(30L));
		
		Proposal proposal = Proposal.create(new ProposalId(0L), couple.getPartnerOneId(), "TEXT", "JUSTIFICATION TEXT", Clock.systemDefaultZone(), ProposalStatus.DRAFT);
		
		Negotiation negotiation = Negotiation.start(new NegotiationId(100L), couple, proposal, couple.getPartnerTwoId());
		
		negotiation.acceptNegotiation(couple.getPartnerTwoId());
		
		assertEquals(negotiation.getCurrentResponder(), couple.getPartnerOneId());
		
		negotiation.sendProposal(couple.getPartnerOneId());
		
		assertEquals(negotiation.getCurrentResponder(), couple.getPartnerTwoId());
	}
	
}
