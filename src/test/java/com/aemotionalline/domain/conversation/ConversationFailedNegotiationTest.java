package com.aemotionalline.domain.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.Ids;
import com.aemotionalline.domain.MutableClock;
import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.conversation.FailedNegotiationRecord.Outcome;
import com.aemotionalline.domain.conversation.NegotiationRecord.Purpose;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.negotiation.AgreementProposal;
import com.aemotionalline.domain.negotiation.AgreementText;
import com.aemotionalline.domain.negotiation.DiscussionProposal;
import com.aemotionalline.domain.negotiation.Negotiation;
import com.aemotionalline.domain.negotiation.Proposal;
import com.aemotionalline.domain.negotiation.ProposalStatus;
import com.aemotionalline.domain.user.UserId;

/**
 * Negotiations that end without an agreement (refused or abandoned) are kept in their own section of the archive,
 * with the proposals that were sent, or the request when none was; never the draft in progress.
 */
public class ConversationFailedNegotiationTest
{
	private static final Couple COUPLE = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));

	private static final UserId A = COUPLE.getPartnerOneId();
	private static final UserId B = COUPLE.getPartnerTwoId();

	private static final Instant T0 = Instant.parse("2026-10-29T10:00:00Z");

	private final MutableClock clock = new MutableClock(T0);

	private Conversation startConversation()
	{
		Negotiation first = Negotiation.start(Ids.negotiation(60L), COUPLE, new AgreementProposal(Ids.proposal(60L), A, "TEXT"), Clock.systemUTC());
		first.acceptNegotiation(B);
		first.sendProposal(A, "JUSTIFICATION");
		first.acceptProposal(B);

		return Conversation.start(Ids.conversation(321L), COUPLE, first, clock);
	}

	private Negotiation agreementRequest(long id, Couple couple)
	{
		return Negotiation.start(Ids.negotiation(id), couple, new AgreementProposal(Ids.proposal(id), couple.getPartnerOneId(), "AGREEMENT " + id), clock);
	}

	private Negotiation discussionRequest(long id)
	{
		return Negotiation.start(Ids.negotiation(id), COUPLE, new DiscussionProposal(Ids.proposal(id), A, "TITLE " + id, "SUBTITLE " + id), clock);
	}

	@Test
	void shouldKeepTheRequestOfARefusedNegotiation()
	{
		Conversation conversation = startConversation();
		Negotiation negotiation = agreementRequest(70L, COUPLE);
		clock.advance(Duration.ofHours(1));
		negotiation.refuseNegotiation(B);

		conversation.recordFailedNegotiation(negotiation);

		FailedNegotiationRecord record = conversation.getFailedNegotiations().getFirst();
		assertEquals(negotiation.getId(), record.negotiationId());
		assertEquals(Purpose.MODIFIED_AGREEMENT, record.attemptedPurpose());
		assertEquals(Outcome.REFUSED, record.outcome());
		assertEquals(B, record.closedBy());
		assertEquals(T0, record.startedAt());
		assertEquals(T0.plus(Duration.ofHours(1)), record.closedAt());
		assertEquals(1, record.proposals().size());
		assertEquals(new AgreementText("AGREEMENT 70"), record.proposals().getFirst().getContent());
	}

	@Test
	void shouldKeepTheSentProposalsOfAnAbandonedNegotiationButNotTheDraft()
	{
		Conversation conversation = startConversation();
		Negotiation negotiation = agreementRequest(70L, COUPLE);
		negotiation.acceptNegotiation(B);
		negotiation.sendProposal(A, "FIRST");
		negotiation.refuseProposal(B, Ids.proposal(71L));
		negotiation.editCurrentProposal(B, new AgreementText("B'S VERSION"));
		negotiation.sendProposal(B, "SECOND");
		negotiation.refuseProposal(A, Ids.proposal(72L));
		negotiation.editCurrentProposal(A, new AgreementText("A'S DRAFT, NEVER SENT"));
		negotiation.abandon(A);

		conversation.recordFailedNegotiation(negotiation);

		FailedNegotiationRecord record = conversation.getFailedNegotiations().getFirst();
		assertEquals(Outcome.ABANDONED, record.outcome());
		assertEquals(A, record.closedBy());
		assertEquals(List.of(Ids.proposal(70L), Ids.proposal(71L)), record.proposals().stream().map(Proposal::getId).toList());
		assertEquals(List.of(ProposalStatus.REFUSED, ProposalStatus.REFUSED), record.proposals().stream().map(Proposal::getProposalStatus).toList());
	}

	@Test
	void shouldKeepTheRequestWhenAbandonedBeforeAnythingWasSent()
	{
		Conversation conversation = startConversation();
		Negotiation negotiation = discussionRequest(70L);
		negotiation.abandon(A);

		conversation.recordFailedNegotiation(negotiation);

		FailedNegotiationRecord record = conversation.getFailedNegotiations().getFirst();
		assertEquals(Purpose.OPENED_DISCUSSION, record.attemptedPurpose());
		assertEquals(Outcome.ABANDONED, record.outcome());
		assertEquals(List.of(negotiation.getRequest()), record.proposals());
	}

	@Test
	void shouldKeepTheFailedNegotiationsInTheirOwnSectionInOrder()
	{
		Conversation conversation = startConversation();
		Negotiation first = agreementRequest(70L, COUPLE);
		first.refuseNegotiation(B);
		Negotiation second = discussionRequest(80L);
		second.abandon(B);

		conversation.recordFailedNegotiation(first);
		conversation.recordFailedNegotiation(second);

		assertEquals(List.of(first.getId(), second.getId()), conversation.getFailedNegotiations().stream().map(FailedNegotiationRecord::negotiationId).toList());
		// The history of what took effect is unchanged.
		assertEquals(1, conversation.getNegotiationHistory().size());
	}

	@Test
	void shouldOnlyRecordANegotiationThatEndedWithoutAnAgreement()
	{
		Conversation conversation = startConversation();

		Negotiation waiting = agreementRequest(70L, COUPLE);
		assertThrows(DomainException.class, () -> conversation.recordFailedNegotiation(waiting));

		Negotiation agreed = agreementRequest(80L, COUPLE);
		agreed.acceptNegotiation(B);
		agreed.sendProposal(A, "JUSTIFICATION");
		agreed.acceptProposal(B);
		assertThrows(DomainException.class, () -> conversation.recordFailedNegotiation(agreed));

		assertThrows(DomainException.class, () -> conversation.recordFailedNegotiation(null));
		assertTrue(conversation.getFailedNegotiations().isEmpty());
	}

	@Test
	void shouldRejectANegotiationOfAnotherCoupleOrOneAlreadyRecorded()
	{
		Conversation conversation = startConversation();
		Couple other = new Couple(Ids.couple(2L), Ids.user(11L), Ids.user(21L), Ids.user(31L));
		Negotiation foreign = agreementRequest(70L, other);
		foreign.refuseNegotiation(other.getPartnerTwoId());
		assertThrows(DomainException.class, () -> conversation.recordFailedNegotiation(foreign));

		Negotiation refused = agreementRequest(80L, COUPLE);
		refused.refuseNegotiation(B);
		conversation.recordFailedNegotiation(refused);
		assertThrows(DomainException.class, () -> conversation.recordFailedNegotiation(refused));

		assertEquals(1, conversation.getFailedNegotiations().size());
	}

	@Test
	void shouldNotLetTheSectionBeChangedFromOutside()
	{
		Conversation conversation = startConversation();
		Negotiation refused = agreementRequest(70L, COUPLE);
		refused.refuseNegotiation(B);
		conversation.recordFailedNegotiation(refused);

		assertThrows(UnsupportedOperationException.class, () -> conversation.getFailedNegotiations().clear());
		assertThrows(UnsupportedOperationException.class, () -> conversation.getFailedNegotiations().getFirst().proposals().clear());
	}
}
