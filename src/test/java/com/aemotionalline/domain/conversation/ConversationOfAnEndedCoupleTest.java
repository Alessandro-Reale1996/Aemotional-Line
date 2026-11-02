package com.aemotionalline.domain.conversation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.MutableClock;
import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.constraint.ConstraintAssignment;
import com.aemotionalline.domain.constraint.ConstraintContext;
import com.aemotionalline.domain.constraint.GeoPosition;
import com.aemotionalline.domain.constraint.TimeConstraint;
import com.aemotionalline.domain.conversation.OutcomeDeclaration.Outcome;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.message.Message;
import com.aemotionalline.domain.message.MessageId;
import com.aemotionalline.domain.negotiation.AgreementProposal;
import com.aemotionalline.domain.negotiation.Negotiation;
import com.aemotionalline.domain.negotiation.NegotiationId;
import com.aemotionalline.domain.negotiation.ProposalId;
import com.aemotionalline.domain.user.UserId;

/**
 * Once the therapist ends the couple, its conversation is read-only and everything pending stays frozen; opening a message
 * is still possible, since it is consultation of the archive.
 */
public class ConversationOfAnEndedCoupleTest
{
	private static final ConstraintContext AWAY = new ConstraintContext(LocalTime.NOON, new GeoPosition(45.48, 9.19));

	private FullConversation full;
	private Conversation conversation;

	@BeforeEach
	void endTheCouple()
	{
		Couple couple = new Couple(CoupleId.random(), UserId.random(), UserId.random(), UserId.random());
		full = new FullConversation(couple, ConversationId.random(), new MutableClock(Instant.parse("2026-11-02T10:00:00Z")));
		conversation = full.conversation;

		couple.end(full.therapist, Instant.parse("2026-11-20T10:00:00Z"));
	}

	@Test
	void shouldRefuseMessagesNegotiationsAndConstraints()
	{
		Message message = new Message(MessageId.random(), full.b);
		message.addParagraph(FullConversation.simple(full.t));
		conversation.readMessage(full.b, full.main, full.lastMainMessage, FullConversation.NOON);

		assertThrows(DomainException.class, () -> conversation.sendMessage(message, full.main, AWAY));
		assertFalse(conversation.canSendMessage(full.b));
		assertThrows(DomainException.class, () -> conversation.openDiscussion(full.discussionNegotiation(NegotiationId.random())));
		assertThrows(DomainException.class, () -> conversation.proposeConstraints(full.therapist,
				List.of(new ConstraintAssignment(full.a, new TimeConstraint(LocalTime.of(9, 0))))));
		assertThrows(DomainException.class, () -> conversation.approveConstraints(full.pendingConstraints, full.b));
		assertThrows(DomainException.class, () -> conversation.withdrawConstraints(full.pendingConstraints, full.therapist));

		// A negotiation that failed just before the end (the same couple, as it was then): it can't be recorded any more.
		Couple beforeTheEnd = new Couple(conversation.getCouple().getId(), full.a, full.b, full.therapist);
		Negotiation failed = Negotiation.start(NegotiationId.random(), beforeTheEnd, new AgreementProposal(ProposalId.random(), full.a, "X"),
				Clock.systemUTC());
		failed.abandon(full.a);
		assertThrows(DomainException.class, () -> conversation.recordFailedNegotiation(failed));
	}

	@Test
	void shouldFreezeThePendingClosingsAndDeclarations()
	{
		assertThrows(DomainException.class, () -> conversation.confirmTherapistClosing(full.pendingBranch, full.therapist));
		assertThrows(DomainException.class, () -> conversation.requestClosing(full.pendingBranch, full.a));
		assertThrows(DomainException.class, () -> conversation.declareOutcome(full.pendingBranch, full.a, Outcome.RESOLVED, "Late."));

		assertTrue(conversation.findDiscussion(full.pendingBranch).getTherapistClosingRequests().getFirst().isPending());
		assertFalse(conversation.findDiscussion(full.pendingBranch).isClosed());
	}

	@Test
	void shouldStillLetAPartnerOpenAMessage()
	{
		assertDoesNotThrow(() -> conversation.readMessage(full.b, full.main, full.lastMainMessage, FullConversation.NOON));

		assertTrue(conversation.findDiscussion(full.main).getReadAt(full.lastMainMessage).isPresent());
	}

	@Test
	void shouldNotStartAConversationForAnEndedCouple()
	{
		Couple ended = new Couple(CoupleId.random(), UserId.random(), UserId.random(), UserId.random());
		Negotiation agreement = Negotiation.start(NegotiationId.random(), ended, new AgreementProposal(ProposalId.random(), ended.getPartnerOneId(), "X"),
				Clock.systemUTC());
		agreement.acceptNegotiation(ended.getPartnerTwoId());
		agreement.sendProposal(ended.getPartnerOneId(), "READY");
		agreement.acceptProposal(ended.getPartnerTwoId());
		ended.end(ended.getTherapistId(), Instant.now());

		assertThrows(DomainException.class, () -> Conversation.start(ConversationId.random(), ended, agreement, Clock.systemUTC()));
	}
}
