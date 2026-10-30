package com.aemotionalline.domain.conversation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.Ids;
import com.aemotionalline.domain.MutableClock;
import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.constraint.ConstraintAssignment;
import com.aemotionalline.domain.constraint.ConstraintChangeRequestStatus;
import com.aemotionalline.domain.constraint.ConstraintContext;
import com.aemotionalline.domain.constraint.GeoPosition;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.message.Message;
import com.aemotionalline.domain.message.MessageId;
import com.aemotionalline.domain.message.Paragraph;
import com.aemotionalline.domain.message.SimpleParagraph;
import com.aemotionalline.domain.negotiation.AgreementText;
import com.aemotionalline.domain.negotiation.Negotiation;
import com.aemotionalline.domain.negotiation.NegotiationId;
import com.aemotionalline.domain.negotiation.Proposal;

/**
 * A conversation rebuilt from its stored facts (every message, paragraph and proposal recreated from scratch, as the
 * persistence layer does) must be indistinguishable from the original and go on working under the same rules.
 */
public class ConversationRestoreTest
{
	private static final Couple COUPLE = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));

	// B has a location constraint on writing: B writes away from home.
	private static final ConstraintContext AWAY = new ConstraintContext(LocalTime.NOON, new GeoPosition(45.48, 9.19));

	private FullConversation full;
	private Conversation original;

	@BeforeEach
	void buildAFullConversation()
	{
		full = new FullConversation(COUPLE, Ids.conversation(321L), new MutableClock(Instant.parse("2026-10-30T10:00:00Z")));
		original = full.conversation;
	}

	private Conversation restored()
	{
		return Conversation.restore(ConversationFacts.snapshot(original), full.clock);
	}

	private static Message message(com.aemotionalline.domain.user.UserId sender, Paragraph... paragraphs)
	{
		Message message = new Message(MessageId.random(), sender);

		for (Paragraph paragraph : paragraphs)
		{
			message.addParagraph(paragraph);
		}

		return message;
	}

	@Test
	void shouldRebuildEveryFactOfTheConversation()
	{
		Conversation restored = restored();

		assertNotSame(original, restored);
		assertEquals(ConversationFacts.fingerprint(original), ConversationFacts.fingerprint(restored));
	}

	@Test
	void shouldDeriveTheAgreementTheConstraintsAndTheLastSenders()
	{
		Conversation restored = restored();

		assertEquals(new AgreementText("B'S AGREEMENT"), restored.getAgreement().getContent());
		assertEquals(List.of(full.a, full.b), restored.getConstraintAssignments().stream().map(ConstraintAssignment::getUserId).toList());
		assertEquals(full.a, restored.findDiscussion(full.main).getLastSender());
		assertEquals(full.a, restored.findDiscussion(full.closedBranch).getLastSender());
	}

	@Test
	void shouldGoOnWithTheSameRulesForMessagesReadsAndConstraints()
	{
		Conversation restored = restored();

		// The last message of the main discussion was never read by B: the reply needs the read first.
		Message reply = message(full.b, FullConversation.simple(full.t));
		assertThrows(DomainException.class, () -> restored.sendMessage(reply, full.main, AWAY));

		restored.readMessage(full.b, full.main, full.lastMainMessage, FullConversation.NOON);

		// B's location constraint survived the restore: at home (no position) B can't write.
		assertThrows(DomainException.class, () -> restored.sendMessage(reply, full.main, FullConversation.NOON));
		assertDoesNotThrow(() -> restored.sendMessage(reply, full.main, AWAY));

		assertThrows(DomainException.class, () -> restored.sendMessage(message(full.b, FullConversation.simple()), full.main, AWAY));
	}

	@Test
	void shouldKeepTheOriginOfAClosedBranchBlockedAndTheBranchClosed()
	{
		Conversation restored = restored();

		assertEquals(List.of(full.closedBranch), restored.discussionsBlockingReplyTo(message(full.b, FullConversation.simple(full.s)), full.main));
		assertTrue(restored.findDiscussion(full.closedBranch).isClosed());
		assertThrows(DomainException.class, () -> restored.sendMessage(message(full.b, FullConversation.simple()), full.closedBranch, AWAY));
	}

	@Test
	void shouldGoOnWithThePendingRequests()
	{
		Conversation restored = restored();

		restored.confirmTherapistClosing(full.pendingBranch, full.therapist);
		assertTrue(restored.findDiscussion(full.pendingBranch).isClosed());

		restored.approveConstraints(full.pendingConstraints, full.b);
		var applied = restored.getConstraintRequestHistory().getLast();
		assertEquals(ConstraintChangeRequestStatus.APPLIED, applied.status());
		assertEquals(List.of(full.b), restored.getConstraintAssignments().stream().map(ConstraintAssignment::getUserId).toList());
		assertEquals(List.of(full.a, full.b), applied.previousAssignments().orElseThrow().stream().map(ConstraintAssignment::getUserId).toList());
	}

	@Test
	void shouldGiveTheSameHistoryByTopicAndUnansweredQuestions()
	{
		Conversation restored = restored();

		assertEquals(TopicHistory.of(original, full.q.getId()).entries().stream().map(entry -> entry.paragraph().getId()).toList(),
				TopicHistory.of(restored, full.q.getId()).entries().stream().map(entry -> entry.paragraph().getId()).toList());
		assertEquals(Analyzer.questionsLeftUnanswered(original).stream().map(Paragraph::getId).toList(),
				Analyzer.questionsLeftUnanswered(restored).stream().map(Paragraph::getId).toList());
	}

	@Test
	void shouldNotAcceptAMessageOrANegotiationThatWasAlreadyUsed()
	{
		Conversation restored = restored();

		assertThrows(DomainException.class, () -> restored.openDiscussion(full.discussionNegotiation(full.closedBranchNegotiation)));

		SimpleParagraph resent = new SimpleParagraph(full.r.getId(), "Title", "Subtitle", "Body");
		restored.readMessage(full.b, full.main, full.lastMainMessage, FullConversation.NOON);
		assertThrows(DomainException.class, () -> restored.sendMessage(message(full.b, resent), full.main, AWAY));
	}

	@Test
	void shouldRejectASnapshotWithoutItsShape()
	{
		ConversationSnapshot snapshot = ConversationFacts.snapshot(original);

		assertThrows(DomainException.class, () -> Conversation.restore(null, full.clock));
		assertThrows(DomainException.class, () -> Conversation.restore(snapshot, null));
		assertThrows(DomainException.class, () -> Conversation.restore(new ConversationSnapshot(snapshot.id(), snapshot.couple(), List.of(),
				snapshot.sendingOrder(), snapshot.negotiationHistory(), snapshot.failedNegotiations(), snapshot.constraintRequests()), full.clock));
		assertThrows(DomainException.class, () -> Conversation.restore(new ConversationSnapshot(snapshot.id(), snapshot.couple(), snapshot.discussions(),
				snapshot.sendingOrder().subList(1, snapshot.sendingOrder().size()), snapshot.negotiationHistory(), snapshot.failedNegotiations(),
				snapshot.constraintRequests()), full.clock));
		assertThrows(DomainException.class, () -> Conversation.restore(new ConversationSnapshot(snapshot.id(), snapshot.couple(), snapshot.discussions(),
				snapshot.sendingOrder(), snapshot.negotiationHistory().subList(1, snapshot.negotiationHistory().size()), snapshot.failedNegotiations(),
				snapshot.constraintRequests()), full.clock));
	}

	@Test
	void shouldGiveProposalsThatCantStartAnotherNegotiation()
	{
		Proposal agreement = restored().getAgreement();

		assertThrows(DomainException.class, () -> Negotiation.start(NegotiationId.random(), COUPLE, agreement, Clock.systemUTC()));
	}
}
