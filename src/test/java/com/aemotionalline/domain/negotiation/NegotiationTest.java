package com.aemotionalline.domain.negotiation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;



import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.user.UserId;

public class NegotiationTest 
{
	// ensureCanAct() is private, so its guards are exercised through acceptNegotiation().
	
	@Test
	void shouldThrowExceptionIfNegotiationStatusIsRefused()
	{
		Couple couple = new Couple(1L, new UserId(10L), new UserId(20L), new UserId(30L));
		
		Proposal proposal = new Proposal(new ProposalId(0L), couple.getPartnerOneId(), "TEXT");
		
		Negotiation negotiation = Negotiation.start(new NegotiationId(100L), couple, proposal);
		
		negotiation.refuseNegotiation(couple.getPartnerTwoId());
		
		assertThrows(DomainException.class, ()-> negotiation.acceptNegotiation(couple.getPartnerTwoId()));
		
	}
	
	@Test
	void shouldThrowExceptionIfProposalStatusIsAccepted()
	{
		Couple couple = new Couple(1L, new UserId(10L), new UserId(20L), new UserId(30L));
		
		Proposal proposal = new Proposal(new ProposalId(0L), couple.getPartnerOneId(), "TEXT");
		
		Negotiation negotiation = Negotiation.start(new NegotiationId(100L), couple, proposal);
		
		negotiation.acceptNegotiation(couple.getPartnerTwoId());
		
		negotiation.sendProposal(couple.getPartnerOneId(), "JUSTIFICATION TEXT");
		
		negotiation.acceptProposal(couple.getPartnerTwoId());
		
		assertThrows(DomainException.class, ()-> negotiation.acceptNegotiation(couple.getPartnerTwoId()));
		
	}
	
	@Test
	void shouldThrowExceptionIfUserIsNotInTheCouple()
	{
		Couple couple = new Couple(1L, new UserId(10L), new UserId(20L), new UserId(30L));
		
		Proposal proposal = new Proposal(new ProposalId(0L), couple.getPartnerOneId(), "TEXT");
		
		Negotiation negotiation = Negotiation.start(new NegotiationId(100L), couple, proposal);
		
		assertThrows(DomainException.class, ()-> negotiation.acceptNegotiation(new UserId(123L)));
	}
	
	@Test
	void shouldThrowExceptionIfUserIsNotTheCurrentResponder()
	{
		Couple couple = new Couple(1L, new UserId(10L), new UserId(20L), new UserId(30L));
		
		Proposal proposal = new Proposal(new ProposalId(0L), couple.getPartnerOneId(), "TEXT");
		
		Negotiation negotiation = Negotiation.start(new NegotiationId(100L), couple, proposal);
		
		assertThrows(DomainException.class, ()-> negotiation.acceptNegotiation(couple.getPartnerOneId()));
	}
	
	@Test 
	void shouldSwitchCurrentResponderAfterAnActionOfTheCorresponder()
	{
		Couple couple = new Couple(1L, new UserId(10L), new UserId(20L), new UserId(30L));
		
		Proposal proposal = new Proposal(new ProposalId(0L), couple.getPartnerOneId(), "TEXT");
		
		Negotiation negotiation = Negotiation.start(new NegotiationId(100L), couple, proposal);
		
		negotiation.acceptNegotiation(couple.getPartnerTwoId());
		
		// The turn returns to the author of the initial proposal, who must now send it.
		assertEquals(negotiation.getCurrentResponder(), couple.getPartnerOneId());
		
		negotiation.sendProposal(couple.getPartnerOneId(), "JUSTIFICATION TEXT");
		
		assertEquals(negotiation.getCurrentResponder(), couple.getPartnerTwoId());
	}

	// PROPOSALS CAN ONLY BE ANSWERED ONCE SENT:

	@Test
	void shouldThrowExceptionWhenGettingLastProposalBeforeAnyIsSent()
	{
		Couple couple = new Couple(1L, new UserId(10L), new UserId(20L), new UserId(30L));

		Proposal proposal = new Proposal(new ProposalId(0L), couple.getPartnerOneId(), "TEXT");

		Negotiation negotiation = Negotiation.start(new NegotiationId(100L), couple, proposal);

		assertThrows(DomainException.class, ()-> negotiation.getLastProposal());
	}

	@Test
	void shouldThrowExceptionWhenAcceptingProposalBeforeAnyIsSent()
	{
		Couple couple = new Couple(1L, new UserId(10L), new UserId(20L), new UserId(30L));

		Proposal proposal = new Proposal(new ProposalId(0L), couple.getPartnerOneId(), "TEXT");

		Negotiation negotiation = Negotiation.start(new NegotiationId(100L), couple, proposal);

		negotiation.acceptNegotiation(couple.getPartnerTwoId());

		// It is partner one's turn, but the archive is still empty: there is nothing to accept.
		assertThrows(DomainException.class, ()-> negotiation.acceptProposal(couple.getPartnerOneId()));
	}

	@Test
	void shouldThrowExceptionWhenRefusingProposalBeforeAnyIsSent()
	{
		Couple couple = new Couple(1L, new UserId(10L), new UserId(20L), new UserId(30L));

		Proposal proposal = new Proposal(new ProposalId(0L), couple.getPartnerOneId(), "TEXT");

		Negotiation negotiation = Negotiation.start(new NegotiationId(100L), couple, proposal);

		negotiation.acceptNegotiation(couple.getPartnerTwoId());

		assertThrows(DomainException.class, ()-> negotiation.refuseProposal(couple.getPartnerOneId(), new ProposalId(1L)));
	}

	@Test
	void shouldThrowExceptionWhenAcceptingARefusedProposal()
	{
		Couple couple = new Couple(1L, new UserId(10L), new UserId(20L), new UserId(30L));

		Proposal proposal = new Proposal(new ProposalId(0L), couple.getPartnerOneId(), "TEXT");

		Negotiation negotiation = Negotiation.start(new NegotiationId(100L), couple, proposal);

		negotiation.acceptNegotiation(couple.getPartnerTwoId());

		negotiation.sendProposal(couple.getPartnerOneId(), "JUSTIFICATION TEXT");

		negotiation.refuseProposal(couple.getPartnerTwoId(), new ProposalId(1L));

		assertThrows(DomainException.class, ()-> negotiation.acceptProposal(couple.getPartnerTwoId()));
	}

	// PROPOSALS ARE EXCHANGED ONLY AFTER THE NEGOTIATION IS ACCEPTED:

	@Test
	void shouldThrowExceptionWhenSendingProposalBeforeNegotiationIsAccepted()
	{
		Couple couple = new Couple(1L, new UserId(10L), new UserId(20L), new UserId(30L));

		Proposal proposal = new Proposal(new ProposalId(0L), couple.getPartnerOneId(), "TEXT");

		Negotiation negotiation = Negotiation.start(new NegotiationId(100L), couple, proposal);

		// Partner two holds the turn but must first accept or refuse the request to talk.
		assertThrows(DomainException.class, ()-> negotiation.sendProposal(couple.getPartnerTwoId(), "JUSTIFICATION TEXT"));

		assertEquals(NegotiationStatus.DRAFT, negotiation.getNegotiationStatus());
	}

	@Test
	void shouldThrowExceptionWhenSettingProposalBeforeNegotiationIsAccepted()
	{
		Couple couple = new Couple(1L, new UserId(10L), new UserId(20L), new UserId(30L));

		Proposal proposal = new Proposal(new ProposalId(0L), couple.getPartnerOneId(), "TEXT");

		Negotiation negotiation = Negotiation.start(new NegotiationId(100L), couple, proposal);

		Proposal counterProposal = new Proposal(new ProposalId(1L), couple.getPartnerTwoId(), "OTHER TEXT");

		assertThrows(DomainException.class, ()-> negotiation.setCurrentProposal(counterProposal));
	}

	@Test
	void shouldThrowExceptionWhenAcceptingNegotiationTwice()
	{
		Couple couple = new Couple(1L, new UserId(10L), new UserId(20L), new UserId(30L));

		Proposal proposal = new Proposal(new ProposalId(0L), couple.getPartnerOneId(), "TEXT");

		Negotiation negotiation = Negotiation.start(new NegotiationId(100L), couple, proposal);

		negotiation.acceptNegotiation(couple.getPartnerTwoId());

		// Partner one now holds the turn; a second acceptance would only flip it back and skip the turn.
		assertThrows(DomainException.class, ()-> negotiation.acceptNegotiation(couple.getPartnerOneId()));

		assertEquals(couple.getPartnerOneId(), negotiation.getCurrentResponder());
	}

	@Test
	void shouldAcceptProposalAfterNegotiationIsAccepted()
	{
		Couple couple = new Couple(1L, new UserId(10L), new UserId(20L), new UserId(30L));

		Proposal proposal = new Proposal(new ProposalId(0L), couple.getPartnerOneId(), "TEXT");

		Negotiation negotiation = Negotiation.start(new NegotiationId(100L), couple, proposal);

		negotiation.acceptNegotiation(couple.getPartnerTwoId());

		negotiation.sendProposal(couple.getPartnerOneId(), "JUSTIFICATION TEXT");

		negotiation.acceptProposal(couple.getPartnerTwoId());

		assertEquals(NegotiationStatus.ACCEPTED, negotiation.getNegotiationStatus());
		assertEquals(ProposalStatus.ACCEPTED, negotiation.getLastProposal().getProposalStatus());
	}

}
