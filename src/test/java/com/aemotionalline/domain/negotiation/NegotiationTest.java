package com.aemotionalline.domain.negotiation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.Ids;

public class NegotiationTest 
{
	// ensureCanAct() is private, so its guards are exercised through acceptNegotiation().
	
	@Test
	void shouldThrowExceptionIfNegotiationStatusIsRefused()
	{
		Couple couple = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));
		
		Proposal proposal = new Proposal(Ids.proposal(0L), couple.getPartnerOneId(), "TEXT");
		
		Negotiation negotiation = Negotiation.start(Ids.negotiation(100L), couple, proposal, Clock.systemUTC());
		
		negotiation.refuseNegotiation(couple.getPartnerTwoId());
		
		assertThrows(DomainException.class, ()-> negotiation.acceptNegotiation(couple.getPartnerTwoId()));
		
	}
	
	@Test
	void shouldThrowExceptionIfProposalStatusIsAccepted()
	{
		Couple couple = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));
		
		Proposal proposal = new Proposal(Ids.proposal(0L), couple.getPartnerOneId(), "TEXT");
		
		Negotiation negotiation = Negotiation.start(Ids.negotiation(100L), couple, proposal, Clock.systemUTC());
		
		negotiation.acceptNegotiation(couple.getPartnerTwoId());
		
		negotiation.sendProposal(couple.getPartnerOneId(), "JUSTIFICATION TEXT");
		
		negotiation.acceptProposal(couple.getPartnerTwoId());
		
		assertThrows(DomainException.class, ()-> negotiation.acceptNegotiation(couple.getPartnerTwoId()));
		
	}
	
	@Test
	void shouldThrowExceptionIfUserIsNotInTheCouple()
	{
		Couple couple = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));
		
		Proposal proposal = new Proposal(Ids.proposal(0L), couple.getPartnerOneId(), "TEXT");
		
		Negotiation negotiation = Negotiation.start(Ids.negotiation(100L), couple, proposal, Clock.systemUTC());
		
		assertThrows(DomainException.class, ()-> negotiation.acceptNegotiation(Ids.user(123L)));
	}
	
	@Test
	void shouldThrowExceptionIfUserIsNotTheCurrentResponder()
	{
		Couple couple = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));
		
		Proposal proposal = new Proposal(Ids.proposal(0L), couple.getPartnerOneId(), "TEXT");
		
		Negotiation negotiation = Negotiation.start(Ids.negotiation(100L), couple, proposal, Clock.systemUTC());
		
		assertThrows(DomainException.class, ()-> negotiation.acceptNegotiation(couple.getPartnerOneId()));
	}
	
	@Test 
	void shouldSwitchCurrentResponderAfterAnActionOfTheCorresponder()
	{
		Couple couple = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));
		
		Proposal proposal = new Proposal(Ids.proposal(0L), couple.getPartnerOneId(), "TEXT");
		
		Negotiation negotiation = Negotiation.start(Ids.negotiation(100L), couple, proposal, Clock.systemUTC());
		
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
		Couple couple = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));

		Proposal proposal = new Proposal(Ids.proposal(0L), couple.getPartnerOneId(), "TEXT");

		Negotiation negotiation = Negotiation.start(Ids.negotiation(100L), couple, proposal, Clock.systemUTC());

		assertThrows(DomainException.class, ()-> negotiation.getLastProposal());
	}

	@Test
	void shouldThrowExceptionWhenAcceptingProposalBeforeAnyIsSent()
	{
		Couple couple = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));

		Proposal proposal = new Proposal(Ids.proposal(0L), couple.getPartnerOneId(), "TEXT");

		Negotiation negotiation = Negotiation.start(Ids.negotiation(100L), couple, proposal, Clock.systemUTC());

		negotiation.acceptNegotiation(couple.getPartnerTwoId());

		// It is partner one's turn, but the archive is still empty: there is nothing to accept.
		assertThrows(DomainException.class, ()-> negotiation.acceptProposal(couple.getPartnerOneId()));
	}

	@Test
	void shouldThrowExceptionWhenRefusingProposalBeforeAnyIsSent()
	{
		Couple couple = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));

		Proposal proposal = new Proposal(Ids.proposal(0L), couple.getPartnerOneId(), "TEXT");

		Negotiation negotiation = Negotiation.start(Ids.negotiation(100L), couple, proposal, Clock.systemUTC());

		negotiation.acceptNegotiation(couple.getPartnerTwoId());

		assertThrows(DomainException.class, ()-> negotiation.refuseProposal(couple.getPartnerOneId(), Ids.proposal(1L)));
	}

	@Test
	void shouldThrowExceptionWhenAcceptingARefusedProposal()
	{
		Couple couple = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));

		Proposal proposal = new Proposal(Ids.proposal(0L), couple.getPartnerOneId(), "TEXT");

		Negotiation negotiation = Negotiation.start(Ids.negotiation(100L), couple, proposal, Clock.systemUTC());

		negotiation.acceptNegotiation(couple.getPartnerTwoId());

		negotiation.sendProposal(couple.getPartnerOneId(), "JUSTIFICATION TEXT");

		negotiation.refuseProposal(couple.getPartnerTwoId(), Ids.proposal(1L));

		assertThrows(DomainException.class, ()-> negotiation.acceptProposal(couple.getPartnerTwoId()));
	}

	// PROPOSALS ARE EXCHANGED ONLY AFTER THE NEGOTIATION IS ACCEPTED:

	@Test
	void shouldThrowExceptionWhenSendingProposalBeforeNegotiationIsAccepted()
	{
		Couple couple = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));

		Proposal proposal = new Proposal(Ids.proposal(0L), couple.getPartnerOneId(), "TEXT");

		Negotiation negotiation = Negotiation.start(Ids.negotiation(100L), couple, proposal, Clock.systemUTC());

		// Partner two holds the turn but must first accept or refuse the request to talk.
		assertThrows(DomainException.class, ()-> negotiation.sendProposal(couple.getPartnerTwoId(), "JUSTIFICATION TEXT"));

		assertEquals(NegotiationStatus.DRAFT, negotiation.getNegotiationStatus());
	}

	@Test
	void shouldThrowExceptionWhenEditingProposalBeforeNegotiationIsAccepted()
	{
		Couple couple = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));

		Proposal proposal = new Proposal(Ids.proposal(0L), couple.getPartnerOneId(), "TEXT");

		Negotiation negotiation = Negotiation.start(Ids.negotiation(100L), couple, proposal, Clock.systemUTC());

		// Partner two holds the turn but must first accept or refuse the request to talk.
		assertThrows(DomainException.class, ()-> negotiation.editCurrentProposal(couple.getPartnerTwoId(), "OTHER TEXT"));
	}

	@Test
	void shouldThrowExceptionWhenAcceptingNegotiationTwice()
	{
		Couple couple = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));

		Proposal proposal = new Proposal(Ids.proposal(0L), couple.getPartnerOneId(), "TEXT");

		Negotiation negotiation = Negotiation.start(Ids.negotiation(100L), couple, proposal, Clock.systemUTC());

		negotiation.acceptNegotiation(couple.getPartnerTwoId());

		// Partner one now holds the turn; a second acceptance would only flip it back and skip the turn.
		assertThrows(DomainException.class, ()-> negotiation.acceptNegotiation(couple.getPartnerOneId()));

		assertEquals(couple.getPartnerOneId(), negotiation.getCurrentResponder());
	}

	@Test
	void shouldAcceptProposalAfterNegotiationIsAccepted()
	{
		Couple couple = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));

		Proposal proposal = new Proposal(Ids.proposal(0L), couple.getPartnerOneId(), "TEXT");

		Negotiation negotiation = Negotiation.start(Ids.negotiation(100L), couple, proposal, Clock.systemUTC());

		negotiation.acceptNegotiation(couple.getPartnerTwoId());

		negotiation.sendProposal(couple.getPartnerOneId(), "JUSTIFICATION TEXT");

		negotiation.acceptProposal(couple.getPartnerTwoId());

		assertEquals(NegotiationStatus.ACCEPTED, negotiation.getNegotiationStatus());
		assertEquals(ProposalStatus.ACCEPTED, negotiation.getLastProposal().getProposalStatus());
	}

	// COUNTER-PROPOSALS: REFUSING CREATES A DRAFT THAT ONLY ITS AUTHOR EDITS AND SENDS:

	@Test
	void shouldCreateDraftFromRefusedProposalWhenRefusing()
	{
		Couple couple = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));

		Proposal proposal = new Proposal(Ids.proposal(0L), couple.getPartnerOneId(), "TEXT");

		Negotiation negotiation = Negotiation.start(Ids.negotiation(100L), couple, proposal, Clock.systemUTC());

		negotiation.acceptNegotiation(couple.getPartnerTwoId());

		negotiation.sendProposal(couple.getPartnerOneId(), "JUSTIFICATION TEXT");

		negotiation.refuseProposal(couple.getPartnerTwoId(), Ids.proposal(1L));

		Proposal draft = negotiation.getCurrentProposal();

		assertEquals(Ids.proposal(1L), draft.getId());
		assertEquals(couple.getPartnerTwoId(), draft.getAuthor());
		assertEquals("TEXT", draft.getText());
		assertEquals(ProposalStatus.DRAFT, draft.getProposalStatus());
		// The refuser keeps the turn: they are now editing and must send the counter-proposal.
		assertEquals(couple.getPartnerTwoId(), negotiation.getCurrentResponder());
	}

	@Test
	void shouldSendEditedCounterProposalAsANewProposal()
	{
		Couple couple = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));

		Proposal proposal = new Proposal(Ids.proposal(0L), couple.getPartnerOneId(), "TEXT");

		Negotiation negotiation = Negotiation.start(Ids.negotiation(100L), couple, proposal, Clock.systemUTC());

		negotiation.acceptNegotiation(couple.getPartnerTwoId());

		negotiation.sendProposal(couple.getPartnerOneId(), "JUSTIFICATION TEXT");

		negotiation.refuseProposal(couple.getPartnerTwoId(), Ids.proposal(1L));

		negotiation.editCurrentProposal(couple.getPartnerTwoId(), "EDITED TEXT");

		negotiation.sendProposal(couple.getPartnerTwoId(), "WHY I CHANGED IT");

		assertEquals(2, negotiation.getProposals().size());
		assertEquals(ProposalStatus.REFUSED, negotiation.getProposals().get(0).getProposalStatus());
		assertEquals("TEXT", negotiation.getProposals().get(0).getText());
		assertEquals("EDITED TEXT", negotiation.getLastProposal().getText());
		assertEquals("WHY I CHANGED IT", negotiation.getLastProposal().getJustification());
		assertEquals(ProposalStatus.WAITING_FOR_RESPONSE, negotiation.getLastProposal().getProposalStatus());
		assertEquals(couple.getPartnerOneId(), negotiation.getCurrentResponder());
		assertNull(negotiation.getCurrentProposal());
	}

	@Test
	void shouldThrowExceptionWhenRefusingWithNullId()
	{
		Couple couple = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));

		Proposal proposal = new Proposal(Ids.proposal(0L), couple.getPartnerOneId(), "TEXT");

		Negotiation negotiation = Negotiation.start(Ids.negotiation(100L), couple, proposal, Clock.systemUTC());

		negotiation.acceptNegotiation(couple.getPartnerTwoId());

		negotiation.sendProposal(couple.getPartnerOneId(), "JUSTIFICATION TEXT");

		assertThrows(DomainException.class, ()-> negotiation.refuseProposal(couple.getPartnerTwoId(), null));

		// The failed call must not leave the proposal half-refused.
		assertEquals(ProposalStatus.WAITING_FOR_RESPONSE, negotiation.getLastProposal().getProposalStatus());
	}

	@Test
	void shouldThrowExceptionWhenRefusingWithAnIdAlreadyInTheArchive()
	{
		Couple couple = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));

		Proposal proposal = new Proposal(Ids.proposal(0L), couple.getPartnerOneId(), "TEXT");

		Negotiation negotiation = Negotiation.start(Ids.negotiation(100L), couple, proposal, Clock.systemUTC());

		negotiation.acceptNegotiation(couple.getPartnerTwoId());

		negotiation.sendProposal(couple.getPartnerOneId(), "JUSTIFICATION TEXT");

		negotiation.refuseProposal(couple.getPartnerTwoId(), Ids.proposal(1L));

		negotiation.sendProposal(couple.getPartnerTwoId(), "JUSTIFICATION TEXT");

		// Id 0 is not the last proposal, but it is still in the archive.
		assertThrows(DomainException.class, ()-> negotiation.refuseProposal(couple.getPartnerOneId(), Ids.proposal(0L)));

		assertEquals(ProposalStatus.WAITING_FOR_RESPONSE, negotiation.getLastProposal().getProposalStatus());
	}

	@Test
	void shouldThrowExceptionWhenEditingWithoutADraft()
	{
		Couple couple = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));

		Proposal proposal = new Proposal(Ids.proposal(0L), couple.getPartnerOneId(), "TEXT");

		Negotiation negotiation = Negotiation.start(Ids.negotiation(100L), couple, proposal, Clock.systemUTC());

		negotiation.acceptNegotiation(couple.getPartnerTwoId());

		negotiation.sendProposal(couple.getPartnerOneId(), "JUSTIFICATION TEXT");

		// Partner two holds the turn, but has not refused yet, so there is no draft to edit.
		assertThrows(DomainException.class, ()-> negotiation.editCurrentProposal(couple.getPartnerTwoId(), "OTHER TEXT"));
	}

	@Test
	void shouldThrowExceptionWhenEditingAfterTheAgreementIsConcluded()
	{
		Couple couple = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));

		Proposal proposal = new Proposal(Ids.proposal(0L), couple.getPartnerOneId(), "TEXT");

		Negotiation negotiation = Negotiation.start(Ids.negotiation(100L), couple, proposal, Clock.systemUTC());

		negotiation.acceptNegotiation(couple.getPartnerTwoId());

		negotiation.sendProposal(couple.getPartnerOneId(), "JUSTIFICATION TEXT");

		negotiation.acceptProposal(couple.getPartnerTwoId());

		assertThrows(DomainException.class, ()-> negotiation.editCurrentProposal(couple.getPartnerTwoId(), "OTHER TEXT"));
	}

	@Test
	void shouldThrowExceptionWhenOtherPartnerEditsTheDraft()
	{
		Couple couple = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));

		Proposal proposal = new Proposal(Ids.proposal(0L), couple.getPartnerOneId(), "TEXT");

		Negotiation negotiation = Negotiation.start(Ids.negotiation(100L), couple, proposal, Clock.systemUTC());

		negotiation.acceptNegotiation(couple.getPartnerTwoId());

		negotiation.sendProposal(couple.getPartnerOneId(), "JUSTIFICATION TEXT");

		negotiation.refuseProposal(couple.getPartnerTwoId(), Ids.proposal(1L));

		assertThrows(DomainException.class, ()-> negotiation.editCurrentProposal(couple.getPartnerOneId(), "OTHER TEXT"));
	}

	@Test
	void shouldThrowExceptionWhenEditingWithBlankText()
	{
		Couple couple = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));

		Proposal proposal = new Proposal(Ids.proposal(0L), couple.getPartnerOneId(), "TEXT");

		Negotiation negotiation = Negotiation.start(Ids.negotiation(100L), couple, proposal, Clock.systemUTC());

		negotiation.acceptNegotiation(couple.getPartnerTwoId());

		assertThrows(DomainException.class, ()-> negotiation.editCurrentProposal(couple.getPartnerOneId(), "   "));

		assertEquals("TEXT", negotiation.getCurrentProposal().getText());
	}

	@Test
	void shouldThrowExceptionWhenNonPartnerStartsTheNegotiation()
	{
		Couple couple = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));

		Proposal proposal = new Proposal(Ids.proposal(0L), Ids.user(999L), "TEXT");

		assertThrows(DomainException.class, ()-> Negotiation.start(Ids.negotiation(100L), couple, proposal, Clock.systemUTC()));
	}

	@Test
	void shouldSetSentAtFromTheClockWhenSending()
	{
		Couple couple = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));

		Proposal proposal = new Proposal(Ids.proposal(0L), couple.getPartnerOneId(), "TEXT");

		Instant now = Instant.parse("2026-10-01T10:00:00Z");

		Negotiation negotiation = Negotiation.start(Ids.negotiation(100L), couple, proposal, Clock.fixed(now, ZoneOffset.UTC));

		negotiation.acceptNegotiation(couple.getPartnerTwoId());

		negotiation.sendProposal(couple.getPartnerOneId(), "JUSTIFICATION TEXT");

		assertEquals(now, negotiation.getLastProposal().getSentAt());
	}

}
