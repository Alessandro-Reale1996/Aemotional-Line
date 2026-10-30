package com.aemotionalline.domain.conversation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.Ids;
import com.aemotionalline.domain.MutableClock;
import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.constraint.ConstraintAssignment;
import com.aemotionalline.domain.constraint.ConstraintChangeRequestId;
import com.aemotionalline.domain.constraint.ConstraintChangeRequestStatus;
import com.aemotionalline.domain.constraint.ConstraintContext;
import com.aemotionalline.domain.constraint.ReplyDelayConstraint;
import com.aemotionalline.domain.constraint.TimeConstraint;
import com.aemotionalline.domain.conversation.OutcomeDeclaration.Outcome;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.message.Message;
import com.aemotionalline.domain.message.Paragraph;
import com.aemotionalline.domain.message.PointedParagraph;
import com.aemotionalline.domain.message.QuestionParagraph;
import com.aemotionalline.domain.message.SimpleParagraph;
import com.aemotionalline.domain.negotiation.AgreementProposal;
import com.aemotionalline.domain.negotiation.AgreementText;
import com.aemotionalline.domain.negotiation.DiscussionProposal;
import com.aemotionalline.domain.negotiation.Negotiation;
import com.aemotionalline.domain.negotiation.Proposal;
import com.aemotionalline.domain.user.UserId;

/**
 * A conversation rebuilt from its stored facts (every message, paragraph and proposal recreated from scratch, as the
 * persistence layer will do) must be indistinguishable from the original and go on working under the same rules.
 */
public class ConversationRestoreTest
{
	private static final Couple COUPLE = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));

	private static final UserId A = COUPLE.getPartnerOneId();
	private static final UserId B = COUPLE.getPartnerTwoId();
	private static final UserId THERAPIST = COUPLE.getTherapistId();

	private static final DiscussionId MAIN = new DiscussionId(Ids.conversation(321L), 0);
	private static final DiscussionId CLOSED_BRANCH = new DiscussionId(Ids.conversation(321L), 1);
	private static final DiscussionId PENDING_BRANCH = new DiscussionId(Ids.conversation(321L), 2);

	private static final Instant T0 = Instant.parse("2026-10-30T10:00:00Z");
	private static final ConstraintContext NOON = new ConstraintContext(LocalTime.NOON);

	private final MutableClock clock = new MutableClock(T0);

	private Conversation original;
	private QuestionParagraph q;
	private SimpleParagraph p, r, s, t;
	private ConstraintChangeRequestId pendingConstraints;
	private List<ConstraintAssignment> firstAssignments;

	private Negotiation agreementNegotiation(long id, String text)
	{
		Negotiation negotiation = Negotiation.start(Ids.negotiation(id), COUPLE, new AgreementProposal(Ids.proposal(id), A, text), clock);
		negotiation.acceptNegotiation(B);
		negotiation.sendProposal(A, "WHY " + id);

		return negotiation;
	}

	private Negotiation discussionNegotiation(long id)
	{
		Negotiation negotiation = Negotiation.start(Ids.negotiation(id), COUPLE, new DiscussionProposal(Ids.proposal(id), A, "TITLE " + id, "SUBTITLE " + id), clock);
		negotiation.acceptNegotiation(B);
		negotiation.sendProposal(A, "WHY " + id);
		negotiation.acceptProposal(B);

		return negotiation;
	}

	private Message send(DiscussionId discussion, long id, UserId sender, Paragraph... paragraphs)
	{
		Message message = new Message(Ids.message(id), sender);

		for (Paragraph paragraph : paragraphs)
		{
			message.addParagraph(paragraph);
		}

		clock.advance(Duration.ofMinutes(1));
		Reading.send(original, message, discussion, NOON);

		return message;
	}

	private static SimpleParagraph simple(long id, Paragraph... answered)
	{
		SimpleParagraph paragraph = new SimpleParagraph(Ids.paragraph(id), "Title " + id, "Subtitle " + id, "Body " + id);

		for (Paragraph reference : answered)
		{
			paragraph.addReference(reference);
		}

		return paragraph;
	}

	// Everything the conversation can hold: earlier failed starts, a changed agreement with a refused intermediate proposal,
	// a failed negotiation, applied/rejected/pending constraint requests, questions answered and skipped, a self-citation,
	// recorded and missing reads, a branch closed by agreement with both declarations, a branch waiting for the therapist.
	@BeforeEach
	void buildAFullConversation()
	{
		Negotiation refusedStart = Negotiation.start(Ids.negotiation(50L), COUPLE, new AgreementProposal(Ids.proposal(50L), A, "TOO EARLY"), clock);
		refusedStart.refuseNegotiation(B);
		clock.advance(Duration.ofDays(1));

		Negotiation start = agreementNegotiation(60L, "FIRST AGREEMENT");
		start.acceptProposal(B);
		original = Conversation.start(Ids.conversation(321L), COUPLE, start, clock, List.of(refusedStart));

		firstAssignments = List.of(new ConstraintAssignment(A, new TimeConstraint(LocalTime.of(9, 0))));
		ConstraintChangeRequestId applied = original.proposeConstraints(THERAPIST, firstAssignments);
		original.approveConstraints(applied, A);
		original.approveConstraints(applied, B);
		ConstraintChangeRequestId rejected = original.proposeConstraints(THERAPIST, List.of(new ConstraintAssignment(B, new ReplyDelayConstraint(Duration.ofHours(2)))));
		original.rejectConstraints(rejected, B);

		q = new QuestionParagraph(Ids.paragraph(1L), "Why?", "Tell me.");
		p = simple(2L);
		QuestionParagraph skipped = new QuestionParagraph(Ids.paragraph(3L), "And then?", "Go on.");
		send(MAIN, 1L, A, q, p, skipped);
		r = simple(5L, p);
		send(MAIN, 2L, B, new PointedParagraph(Ids.paragraph(4L), q, "Because."), r);
		s = simple(6L, r);
		s.addSelfCitation(p);
		t = simple(7L);
		send(MAIN, 3L, A, s, t);

		Negotiation change = agreementNegotiation(70L, "SECOND AGREEMENT");
		change.refuseProposal(B, Ids.proposal(71L));
		change.editCurrentProposal(B, new AgreementText("B'S AGREEMENT"));
		change.sendProposal(B, "BETTER");
		change.acceptProposal(A);
		clock.advance(Duration.ofMinutes(1));
		original.modifyAgreement(change);

		Negotiation abandoned = Negotiation.start(Ids.negotiation(75L), COUPLE, new DiscussionProposal(Ids.proposal(75L), B, "NEVER", "MIND"), clock);
		abandoned.abandon(B);
		original.recordFailedNegotiation(abandoned);

		original.openDiscussion(discussionNegotiation(80L), s.getId());
		SimpleParagraph x = simple(8L, s);
		send(CLOSED_BRANCH, 4L, B, x);
		send(CLOSED_BRANCH, 5L, A, simple(9L, x));
		clock.advance(Duration.ofMinutes(1));
		original.requestClosing(CLOSED_BRANCH, A);
		original.acceptClosing(CLOSED_BRANCH, B);
		original.declareOutcome(CLOSED_BRANCH, A, Outcome.RESOLVED, "Agreed.");
		original.declareOutcome(CLOSED_BRANCH, B, Outcome.STALLED, "Not really.");

		original.openDiscussion(discussionNegotiation(90L));
		send(PENDING_BRANCH, 6L, A, simple(10L));
		original.requestClosing(PENDING_BRANCH, B);
		original.refuseClosing(PENDING_BRANCH, A);
		original.requestTherapistClosing(PENDING_BRANCH, B, "A refuses every time.");

		pendingConstraints = original.proposeConstraints(THERAPIST, List.of(new ConstraintAssignment(B, new TimeConstraint(LocalTime.of(8, 0)))));
		original.approveConstraints(pendingConstraints, A);
	}

	private Conversation restored()
	{
		return Conversation.restore(ConversationFacts.snapshot(original), clock);
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
		assertEquals(1, restored.getConstraintAssignments().size());
		assertEquals(A, restored.getConstraintAssignments().getFirst().getUserId());
		assertEquals(A, restored.findDiscussion(MAIN).getLastSender());
		assertEquals(A, restored.findDiscussion(CLOSED_BRANCH).getLastSender());
	}

	@Test
	void shouldGoOnWithTheSameRulesForMessagesAndReads()
	{
		Conversation restored = restored();

		// The last message of the main discussion was never read by B: the reply needs the read first.
		Message reply = new Message(Ids.message(10L), B);
		reply.addParagraph(simple(20L, t));
		assertThrows(DomainException.class, () -> restored.sendMessage(reply, MAIN, NOON));

		restored.readMessage(B, MAIN, Ids.message(3L), NOON);
		assertDoesNotThrow(() -> restored.sendMessage(reply, MAIN, NOON));

		Message again = new Message(Ids.message(11L), B);
		again.addParagraph(simple(21L));
		assertThrows(DomainException.class, () -> restored.sendMessage(again, MAIN, NOON));
	}

	@Test
	void shouldKeepTheOriginOfAClosedBranchBlockedAndTheBranchClosed()
	{
		Conversation restored = restored();
		Message draft = new Message(Ids.message(10L), B);
		draft.addParagraph(simple(20L, s));

		assertEquals(List.of(CLOSED_BRANCH), restored.discussionsBlockingReplyTo(draft, MAIN));
		assertTrue(restored.findDiscussion(CLOSED_BRANCH).isClosed());

		Message toClosed = new Message(Ids.message(11L), B);
		toClosed.addParagraph(simple(21L));
		assertThrows(DomainException.class, () -> restored.sendMessage(toClosed, CLOSED_BRANCH, NOON));
	}

	@Test
	void shouldGoOnWithThePendingRequests()
	{
		Conversation restored = restored();

		restored.confirmTherapistClosing(PENDING_BRANCH, THERAPIST);
		assertTrue(restored.findDiscussion(PENDING_BRANCH).isClosed());

		restored.approveConstraints(pendingConstraints, B);
		var applied = restored.getConstraintRequestHistory().getLast();
		assertEquals(ConstraintChangeRequestStatus.APPLIED, applied.status());
		assertEquals(B, restored.getConstraintAssignments().getFirst().getUserId());
		assertEquals(List.of(A), applied.previousAssignments().orElseThrow().stream().map(ConstraintAssignment::getUserId).toList());
	}

	@Test
	void shouldGiveTheSameHistoryByTopicAndUnansweredQuestions()
	{
		Conversation restored = restored();

		assertEquals(TopicHistory.of(original, q.getId()).entries().stream().map(entry -> entry.paragraph().getId()).toList(),
				TopicHistory.of(restored, q.getId()).entries().stream().map(entry -> entry.paragraph().getId()).toList());
		assertEquals(Analyzer.questionsLeftUnanswered(original).stream().map(Paragraph::getId).toList(),
				Analyzer.questionsLeftUnanswered(restored).stream().map(Paragraph::getId).toList());
	}

	@Test
	void shouldNotAcceptAMessageOrANegotiationThatWasAlreadyUsed()
	{
		Conversation restored = restored();

		assertThrows(DomainException.class, () -> restored.openDiscussion(discussionNegotiation(80L)));

		Message resent = new Message(Ids.message(12L), B);
		resent.addParagraph(simple(5L));
		restored.readMessage(B, MAIN, Ids.message(3L), NOON);
		assertThrows(DomainException.class, () -> restored.sendMessage(resent, MAIN, NOON));
	}

	@Test
	void shouldRejectASnapshotWithoutItsShape()
	{
		ConversationSnapshot full = ConversationFacts.snapshot(original);

		assertThrows(DomainException.class, () -> Conversation.restore(null, clock));
		assertThrows(DomainException.class, () -> Conversation.restore(full, null));
		assertThrows(DomainException.class, () -> Conversation.restore(new ConversationSnapshot(full.id(), full.couple(), List.of(),
				full.sendingOrder(), full.negotiationHistory(), full.failedNegotiations(), full.constraintRequests()), clock));
		assertThrows(DomainException.class, () -> Conversation.restore(new ConversationSnapshot(full.id(), full.couple(), full.discussions(),
				full.sendingOrder().subList(1, full.sendingOrder().size()), full.negotiationHistory(), full.failedNegotiations(), full.constraintRequests()), clock));
		assertThrows(DomainException.class, () -> Conversation.restore(new ConversationSnapshot(full.id(), full.couple(), full.discussions(),
				full.sendingOrder(), full.negotiationHistory().subList(1, full.negotiationHistory().size()), full.failedNegotiations(), full.constraintRequests()), clock));
	}

	@Test
	void shouldGiveProposalsThatCantStartAnotherNegotiation()
	{
		Proposal agreement = restored().getAgreement();

		assertThrows(DomainException.class, () -> Negotiation.start(Ids.negotiation(99L), COUPLE, agreement, Clock.systemUTC()));
	}
}
