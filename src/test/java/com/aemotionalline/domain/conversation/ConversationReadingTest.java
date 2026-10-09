package com.aemotionalline.domain.conversation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.Ids;
import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.constraint.Constraint;
import com.aemotionalline.domain.constraint.ConstraintAssignment;
import com.aemotionalline.domain.constraint.ConstraintChangeRequest;
import com.aemotionalline.domain.constraint.ConstraintContext;
import com.aemotionalline.domain.constraint.ConstraintScope;
import com.aemotionalline.domain.constraint.ReplyDelayConstraint;
import com.aemotionalline.domain.constraint.TimeConstraint;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.message.Message;
import com.aemotionalline.domain.message.SimpleParagraph;
import com.aemotionalline.domain.negotiation.AgreementProposal;
import com.aemotionalline.domain.negotiation.Negotiation;
import com.aemotionalline.domain.user.UserId;

/**
 * Reading is an operation of its own: a reply needs the message being answered to be read, the constraints of the reader
 * apply to the read, and the instant of the first read feeds the reply-delay constraint.
 */
public class ConversationReadingTest
{
	private static final Couple COUPLE = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));

	private static final UserId A = COUPLE.getPartnerOneId();
	private static final UserId B = COUPLE.getPartnerTwoId();
	private static final UserId THERAPIST = COUPLE.getTherapistId();

	private static final DiscussionId FIRST = new DiscussionId(Ids.conversation(321L), 0);

	private static final Instant T0 = Instant.parse("2026-10-09T10:00:00Z");

	private static ConstraintContext at(LocalTime time, Instant now)
	{
		return new ConstraintContext(time, now);
	}

	private static ConstraintContext at(Instant now)
	{
		return at(LocalTime.NOON, now);
	}

	// A's first message is sent; B has not read it.
	private static Conversation conversationWithMessageFromA(Message first)
	{
		Negotiation negotiation = Negotiation.start(Ids.negotiation(60L), COUPLE, new AgreementProposal(Ids.proposal(60L), A, "TEXT"), Clock.systemUTC());
		negotiation.acceptNegotiation(B);
		negotiation.sendProposal(A, "JUSTIFICATION TEXT");
		negotiation.acceptProposal(B);

		Conversation conversation = Conversation.start(Ids.conversation(321L), COUPLE, negotiation);
		conversation.sendMessage(first, FIRST, new ConstraintContext(LocalTime.NOON));

		return conversation;
	}

	private static Message message(long id, UserId sender)
	{
		Message message = new Message(Ids.message(id), sender);
		message.addParagraph(new SimpleParagraph(Ids.paragraph(id), "Title", "Subtitle", "Body"));

		return message;
	}

	private static void constrain(Conversation conversation, UserId user, Constraint constraint)
	{
		ConstraintChangeRequest request = new ConstraintChangeRequest(THERAPIST, List.of(new ConstraintAssignment(user, constraint)), COUPLE);
		request.approve(A);
		request.approve(B);
		conversation.applyConstraints(request);
	}

	// READING:

	@Test
	void shouldRecordTheFirstReadOfAMessage()
	{
		Message first = message(1L, A);
		Conversation conversation = conversationWithMessageFromA(first);

		conversation.readMessage(B, FIRST, first.getId(), at(T0));
		conversation.readMessage(B, FIRST, first.getId(), at(T0.plusSeconds(60)));

		assertEquals(Optional.of(T0), conversation.findDiscussion(FIRST).getReadAt(first.getId()));
	}

	@Test
	void shouldNotRecordAReadOfTheSendersOwnMessageOrOfTheTherapist()
	{
		Message first = message(1L, A);
		Conversation conversation = conversationWithMessageFromA(first);

		conversation.readMessage(A, FIRST, first.getId(), at(T0));
		conversation.readMessage(THERAPIST, FIRST, first.getId(), at(T0));

		assertEquals(Optional.empty(), conversation.findDiscussion(FIRST).getReadAt(first.getId()));
	}

	@Test
	void shouldRejectAReadByAStrangerOfAnUnknownMessageOrWithoutTheInstant()
	{
		Message first = message(1L, A);
		Conversation conversation = conversationWithMessageFromA(first);

		assertThrows(DomainException.class, () -> conversation.readMessage(Ids.user(99L), FIRST, first.getId(), at(T0)));
		assertThrows(DomainException.class, () -> conversation.readMessage(B, FIRST, Ids.message(99L), at(T0)));
		assertThrows(DomainException.class, () -> conversation.readMessage(B, FIRST, first.getId(), new ConstraintContext(LocalTime.NOON)));
		assertThrows(DomainException.class, () -> conversation.readMessage(B, FIRST, first.getId(), null));
		assertThrows(DomainException.class, () -> conversation.readMessage(null, FIRST, first.getId(), at(T0)));
		assertEquals(Optional.empty(), conversation.findDiscussion(FIRST).getReadAt(first.getId()));
	}

	@Test
	void shouldRejectAReadThatBreaksAReadingConstraintAndRecordNothing()
	{
		Message first = message(1L, A);
		Conversation conversation = conversationWithMessageFromA(first);
		constrain(conversation, B, new TimeConstraint(LocalTime.of(18, 0), ConstraintScope.READ));

		assertThrows(DomainException.class, () -> conversation.readMessage(B, FIRST, first.getId(), at(LocalTime.NOON, T0)));
		assertEquals(Optional.empty(), conversation.findDiscussion(FIRST).getReadAt(first.getId()));

		assertDoesNotThrow(() -> conversation.readMessage(B, FIRST, first.getId(), at(LocalTime.of(18, 0), T0)));
		assertEquals(Optional.of(T0), conversation.findDiscussion(FIRST).getReadAt(first.getId()));
	}

	@Test
	void shouldNotLetAWritingConstraintBlockReadingNorAReadingConstraintBlockWriting()
	{
		Message first = message(1L, A);
		Conversation conversation = conversationWithMessageFromA(first);
		constrain(conversation, B, new TimeConstraint(LocalTime.of(18, 0)));

		assertDoesNotThrow(() -> conversation.readMessage(B, FIRST, first.getId(), at(LocalTime.NOON, T0)));
		assertThrows(DomainException.class, () -> conversation.sendMessage(message(2L, B), FIRST, at(LocalTime.NOON, T0)));
	}

	// REPLYING:

	@Test
	void shouldNotAcceptAReplyToAMessageThatWasNotRead()
	{
		Message first = message(1L, A);
		Conversation conversation = conversationWithMessageFromA(first);

		assertThrows(DomainException.class, () -> conversation.sendMessage(message(2L, B), FIRST, at(T0)));
		assertEquals(1, conversation.findDiscussion(FIRST).getMessages().size());
		assertEquals(1, conversation.getConversationGraph().getParagraphs().size());
	}

	@Test
	void shouldAcceptAReplyOnceTheMessageWasRead()
	{
		Message first = message(1L, A);
		Conversation conversation = conversationWithMessageFromA(first);

		conversation.readMessage(B, FIRST, first.getId(), at(T0));

		assertDoesNotThrow(() -> conversation.sendMessage(message(2L, B), FIRST, at(T0)));
	}

	@Test
	void shouldRequireTheNextReplyToReadTheLatestMessageToo()
	{
		Message first = message(1L, A);
		Conversation conversation = conversationWithMessageFromA(first);
		conversation.readMessage(B, FIRST, first.getId(), at(T0));
		Message second = message(2L, B);
		conversation.sendMessage(second, FIRST, at(T0));

		// Having read the first message doesn't count for the second.
		assertThrows(DomainException.class, () -> conversation.sendMessage(message(3L, A), FIRST, at(T0)));

		conversation.readMessage(A, FIRST, second.getId(), at(T0));
		assertDoesNotThrow(() -> conversation.sendMessage(message(3L, A), FIRST, at(T0)));
	}

	// REPLY DELAY:

	@Test
	void shouldHoldTheReplyUntilTheDelayHasPassedSinceTheRead()
	{
		Message first = message(1L, A);
		Conversation conversation = conversationWithMessageFromA(first);
		constrain(conversation, B, new ReplyDelayConstraint(Duration.ofHours(1)));
		conversation.readMessage(B, FIRST, first.getId(), at(T0));

		assertThrows(DomainException.class, () -> conversation.sendMessage(message(2L, B), FIRST, at(T0.plus(Duration.ofMinutes(59)))));
		assertEquals(1, conversation.findDiscussion(FIRST).getMessages().size());

		// Exactly at the limit is allowed; the rejected tries didn't move the read.
		assertDoesNotThrow(() -> conversation.sendMessage(message(2L, B), FIRST, at(T0.plus(Duration.ofHours(1)))));
	}

	@Test
	void shouldMeasureTheDelayFromTheFirstReadNotFromTheLast()
	{
		Message first = message(1L, A);
		Conversation conversation = conversationWithMessageFromA(first);
		constrain(conversation, B, new ReplyDelayConstraint(Duration.ofHours(1)));
		conversation.readMessage(B, FIRST, first.getId(), at(T0));
		conversation.readMessage(B, FIRST, first.getId(), at(T0.plus(Duration.ofMinutes(50))));

		assertDoesNotThrow(() -> conversation.sendMessage(message(2L, B), FIRST, at(T0.plus(Duration.ofMinutes(61)))));
	}

	@Test
	void shouldFailTheDelayWhenTheContextHasNoCurrentInstant()
	{
		Message first = message(1L, A);
		Conversation conversation = conversationWithMessageFromA(first);
		constrain(conversation, B, new ReplyDelayConstraint(Duration.ZERO));
		conversation.readMessage(B, FIRST, first.getId(), at(T0));

		assertThrows(DomainException.class, () -> conversation.sendMessage(message(2L, B), FIRST, new ConstraintContext(LocalTime.NOON)));
	}

	@Test
	void shouldNotAcceptANullOrNegativeDelay()
	{
		assertThrows(DomainException.class, () -> new ReplyDelayConstraint(null));
		assertThrows(DomainException.class, () -> new ReplyDelayConstraint(Duration.ofSeconds(-1)));
	}
}
