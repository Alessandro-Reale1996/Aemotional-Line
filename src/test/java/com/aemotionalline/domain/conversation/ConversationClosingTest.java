package com.aemotionalline.domain.conversation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.Ids;
import com.aemotionalline.domain.MutableClock;
import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.constraint.ConstraintContext;
import com.aemotionalline.domain.conversation.ClosingRequestRecord.Status;
import com.aemotionalline.domain.conversation.OutcomeDeclaration.Outcome;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.message.Message;
import com.aemotionalline.domain.message.SimpleParagraph;
import com.aemotionalline.domain.negotiation.AgreementProposal;
import com.aemotionalline.domain.negotiation.DiscussionProposal;
import com.aemotionalline.domain.negotiation.Negotiation;
import com.aemotionalline.domain.negotiation.Proposal;
import com.aemotionalline.domain.user.UserId;

/**
 * Nobody closes a specific discussion alone: the other partner accepts a closing request, or, after a failed attempt,
 * the therapist confirms a special request that shows every earlier attempt with its instants. Each partner then
 * declares whether the topic was resolved or stalled.
 */
public class ConversationClosingTest
{
	private static final Couple COUPLE = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));

	private static final UserId A = COUPLE.getPartnerOneId();
	private static final UserId B = COUPLE.getPartnerTwoId();
	private static final UserId THERAPIST = COUPLE.getTherapistId();

	private static final DiscussionId MAIN = new DiscussionId(Ids.conversation(321L), 0);
	private static final DiscussionId SPECIFIC = new DiscussionId(Ids.conversation(321L), 1);

	private static final Instant T0 = Instant.parse("2026-10-29T10:00:00Z");
	private static final ConstraintContext NOON = new ConstraintContext(LocalTime.NOON);

	private final MutableClock clock = new MutableClock(T0);

	private Conversation conversation;

	private static Negotiation settled(Proposal initial, long id)
	{
		Negotiation negotiation = Negotiation.start(Ids.negotiation(id), COUPLE, initial, Clock.systemUTC());
		negotiation.acceptNegotiation(B);
		negotiation.sendProposal(A, "JUSTIFICATION");
		negotiation.acceptProposal(B);

		return negotiation;
	}

	private static Negotiation discussion(long id)
	{
		return settled(new DiscussionProposal(Ids.proposal(id), A, "TITLE " + id, "SUBTITLE " + id), id);
	}

	private static Message message(long id, UserId sender, SimpleParagraph... paragraphs)
	{
		Message message = new Message(Ids.message(id), sender);

		for (SimpleParagraph paragraph : paragraphs)
		{
			message.addParagraph(paragraph);
		}

		return message;
	}

	private static SimpleParagraph paragraph(long id)
	{
		return new SimpleParagraph(Ids.paragraph(id), "Title", "Subtitle", "Body");
	}

	// The clock moves one minute before each action, so every instant is distinct.
	private Instant tick()
	{
		clock.advance(Duration.ofMinutes(1));
		return clock.instant();
	}

	@BeforeEach
	void startWithASpecificDiscussion()
	{
		conversation = Conversation.start(Ids.conversation(321L), COUPLE, settled(new AgreementProposal(Ids.proposal(60L), A, "TEXT"), 60L), clock);
		conversation.openDiscussion(discussion(70L));
	}

	private Discussion specific()
	{
		return conversation.findDiscussion(SPECIFIC);
	}

	// BY AGREEMENT:

	@Test
	void shouldCloseWhenTheOtherPartnerAccepts()
	{
		Instant sent = tick();
		conversation.requestClosing(SPECIFIC, A);
		Instant accepted = tick();
		conversation.acceptClosing(SPECIFIC, B);

		assertTrue(specific().isClosed());
		assertEquals(Optional.of(new DiscussionClosure(DiscussionClosure.Way.AGREEMENT, accepted, List.of())), specific().getClosure());
		assertEquals(List.of(new ClosingRequestRecord(A, sent, Status.ACCEPTED, Optional.of(accepted))), specific().getClosingRequests());
	}

	@Test
	void shouldKeepBothDeclarationsEvenWhenTheyDiffer()
	{
		conversation.requestClosing(SPECIFIC, A);
		conversation.acceptClosing(SPECIFIC, B);

		Instant first = tick();
		conversation.declareOutcome(SPECIFIC, A, Outcome.RESOLVED, "We found a compromise.");
		Instant second = tick();
		conversation.declareOutcome(SPECIFIC, B, Outcome.STALLED, "I gave in.");

		assertEquals(List.of(
				new OutcomeDeclaration(A, Outcome.RESOLVED, "We found a compromise.", first),
				new OutcomeDeclaration(B, Outcome.STALLED, "I gave in.", second)),
				specific().getClosure().get().declarations());
	}

	@Test
	void shouldNotLetTheRequesterAcceptOrRefuseTheirOwnRequest()
	{
		conversation.requestClosing(SPECIFIC, A);

		assertThrows(DomainException.class, () -> conversation.acceptClosing(SPECIFIC, A));
		assertThrows(DomainException.class, () -> conversation.refuseClosing(SPECIFIC, A));
		assertFalse(specific().isClosed());
		assertTrue(specific().getClosingRequests().getFirst().isPending());
	}

	@Test
	void shouldStayOpenWhenTheOtherPartnerRefuses()
	{
		Instant sent = tick();
		conversation.requestClosing(SPECIFIC, A);
		Instant refused = tick();
		conversation.refuseClosing(SPECIFIC, B);

		assertFalse(specific().isClosed());
		assertEquals(List.of(new ClosingRequestRecord(A, sent, Status.REFUSED, Optional.of(refused))), specific().getClosingRequests());
		assertDoesNotThrow(() -> conversation.sendMessage(message(1L, A, paragraph(1L)), SPECIFIC, NOON));
	}

	@Test
	void shouldRecordWhenARequestWasWithdrawnAndOnlyByItsRequester()
	{
		Instant sent = tick();
		conversation.requestClosing(SPECIFIC, A);

		assertThrows(DomainException.class, () -> conversation.withdrawClosing(SPECIFIC, B));

		Instant withdrawn = tick();
		conversation.withdrawClosing(SPECIFIC, A);

		assertEquals(List.of(new ClosingRequestRecord(A, sent, Status.WITHDRAWN, Optional.of(withdrawn))), specific().getClosingRequests());
		assertThrows(DomainException.class, () -> conversation.acceptClosing(SPECIFIC, B));
	}

	@Test
	void shouldAcceptMessagesWhileARequestIsPendingAndNoneOnceClosed()
	{
		conversation.requestClosing(SPECIFIC, A);
		Message first = message(1L, A, paragraph(1L));
		conversation.sendMessage(first, SPECIFIC, NOON);

		conversation.acceptClosing(SPECIFIC, B);

		assertThrows(DomainException.class, () -> Reading.send(conversation, message(2L, B, paragraph(2L)), SPECIFIC, NOON));
		assertEquals(1, specific().getMessages().size());
		// Reading stays possible.
		assertDoesNotThrow(() -> conversation.readMessage(B, SPECIFIC, first.getId(), NOON));
	}

	@Test
	void shouldNeverCloseTheMainDiscussion()
	{
		assertThrows(DomainException.class, () -> conversation.requestClosing(MAIN, A));
		assertThrows(DomainException.class, () -> conversation.requestTherapistClosing(MAIN, A, "Please."));
		assertTrue(conversation.findDiscussion(MAIN).getClosingRequests().isEmpty());
	}

	@Test
	void shouldAllowOnlyOnePendingRequestOfAnyKind()
	{
		conversation.requestClosing(SPECIFIC, A);
		assertThrows(DomainException.class, () -> conversation.requestClosing(SPECIFIC, B));
		conversation.refuseClosing(SPECIFIC, B);

		conversation.requestTherapistClosing(SPECIFIC, A, "B never wants to close.");
		assertThrows(DomainException.class, () -> conversation.requestClosing(SPECIFIC, B));
		assertThrows(DomainException.class, () -> conversation.requestTherapistClosing(SPECIFIC, A, "Again."));
		assertEquals(1, specific().getClosingRequests().size());
	}

	@Test
	void shouldLetOnlyPartnersRequestAndAnswer()
	{
		assertThrows(DomainException.class, () -> conversation.requestClosing(SPECIFIC, THERAPIST));
		assertThrows(DomainException.class, () -> conversation.requestClosing(SPECIFIC, Ids.user(99L)));
		assertThrows(DomainException.class, () -> conversation.requestClosing(SPECIFIC, null));

		conversation.requestClosing(SPECIFIC, A);
		assertThrows(DomainException.class, () -> conversation.acceptClosing(SPECIFIC, THERAPIST));
		assertFalse(specific().isClosed());
	}

	@Test
	void shouldNotRequestTheClosingOfAClosedDiscussion()
	{
		conversation.requestClosing(SPECIFIC, A);
		conversation.acceptClosing(SPECIFIC, B);

		assertThrows(DomainException.class, () -> conversation.requestClosing(SPECIFIC, B));
		assertThrows(DomainException.class, () -> conversation.requestTherapistClosing(SPECIFIC, B, "Close it."));
	}

	// THROUGH THE THERAPIST:

	@Test
	void shouldAskTheTherapistOnlyAfterAFailedAttemptOfTheRequester()
	{
		assertThrows(DomainException.class, () -> conversation.requestTherapistClosing(SPECIFIC, A, "Please close it."));

		conversation.requestClosing(SPECIFIC, A);
		conversation.refuseClosing(SPECIFIC, B);

		// B has not tried: B's refusal of A's request is not an attempt of B's.
		assertThrows(DomainException.class, () -> conversation.requestTherapistClosing(SPECIFIC, B, "Please close it."));
		assertDoesNotThrow(() -> conversation.requestTherapistClosing(SPECIFIC, A, "Please close it."));
	}

	@Test
	void shouldShowTheTherapistEveryEarlierRequestWithItsInstants()
	{
		Instant aSent = tick();
		conversation.requestClosing(SPECIFIC, A);
		Instant aWithdrawn = tick();
		conversation.withdrawClosing(SPECIFIC, A);
		Instant bSent = tick();
		conversation.requestClosing(SPECIFIC, B);
		Instant bRefused = tick();
		conversation.refuseClosing(SPECIFIC, A);

		Instant asked = tick();
		conversation.requestTherapistClosing(SPECIFIC, A, "I asked and then took it back; B's request I refused.");

		TherapistClosingRequestRecord request = specific().getTherapistClosingRequests().getFirst();
		assertEquals(A, request.requester());
		assertEquals(asked, request.sentAt());
		assertEquals(List.of(
				new ClosingRequestRecord(A, aSent, Status.WITHDRAWN, Optional.of(aWithdrawn)),
				new ClosingRequestRecord(B, bSent, Status.REFUSED, Optional.of(bRefused))),
				request.previousRequests());
		assertTrue(request.isPending());
	}

	@Test
	void shouldCloseWhenTheTherapistConfirmsAndStillAskBothPartnersForTheOutcome()
	{
		conversation.requestClosing(SPECIFIC, A);
		conversation.refuseClosing(SPECIFIC, B);
		conversation.requestTherapistClosing(SPECIFIC, A, "B never wants to close.");

		assertThrows(DomainException.class, () -> conversation.confirmTherapistClosing(SPECIFIC, A));

		Instant confirmed = tick();
		conversation.confirmTherapistClosing(SPECIFIC, THERAPIST);

		assertEquals(DiscussionClosure.Way.THERAPIST, specific().getClosure().get().way());
		assertEquals(confirmed, specific().getClosure().get().closedAt());
		assertEquals(TherapistClosingRequestRecord.Status.CONFIRMED, specific().getTherapistClosingRequests().getFirst().status());
		assertEquals(Optional.of(confirmed), specific().getTherapistClosingRequests().getFirst().decidedAt());

		conversation.declareOutcome(SPECIFIC, A, Outcome.STALLED, "We were going in circles.");
		conversation.declareOutcome(SPECIFIC, B, Outcome.STALLED, "I didn't want to stop.");
		assertEquals(2, specific().getClosure().get().declarations().size());
	}

	@Test
	void shouldStayOpenWhenTheTherapistRefusesAndRequireANewFailedAttempt()
	{
		conversation.requestClosing(SPECIFIC, A);
		conversation.refuseClosing(SPECIFIC, B);
		conversation.requestTherapistClosing(SPECIFIC, A, "B never wants to close.");

		conversation.refuseTherapistClosing(SPECIFIC, THERAPIST);

		assertFalse(specific().isClosed());
		assertEquals(TherapistClosingRequestRecord.Status.REFUSED, specific().getTherapistClosingRequests().getFirst().status());
		assertThrows(DomainException.class, () -> conversation.requestTherapistClosing(SPECIFIC, A, "Again."));

		conversation.requestClosing(SPECIFIC, A);
		conversation.refuseClosing(SPECIFIC, B);

		assertDoesNotThrow(() -> conversation.requestTherapistClosing(SPECIFIC, A, "Tried again."));
		assertEquals(2, specific().getTherapistClosingRequests().size());
	}

	@Test
	void shouldLetOnlyTheTherapistDecideAPendingSpecialRequest()
	{
		assertThrows(DomainException.class, () -> conversation.confirmTherapistClosing(SPECIFIC, THERAPIST));

		conversation.requestClosing(SPECIFIC, A);
		conversation.refuseClosing(SPECIFIC, B);
		conversation.requestTherapistClosing(SPECIFIC, A, "B never wants to close.");

		assertThrows(DomainException.class, () -> conversation.refuseTherapistClosing(SPECIFIC, B));
		assertThrows(DomainException.class, () -> conversation.confirmTherapistClosing(SPECIFIC, Ids.user(99L)));
		assertFalse(specific().isClosed());
	}

	// LENGTHS AND DECLARATIONS:

	@Test
	void shouldRequireAJustificationAsLongAsANormalMessageAtMost()
	{
		conversation.requestClosing(SPECIFIC, A);
		conversation.refuseClosing(SPECIFIC, B);

		assertThrows(DomainException.class, () -> conversation.requestTherapistClosing(SPECIFIC, A, " "));
		assertThrows(DomainException.class, () -> conversation.requestTherapistClosing(SPECIFIC, A, null));
		assertThrows(DomainException.class, () -> conversation.requestTherapistClosing(SPECIFIC, A, "x".repeat(801)));
		assertTrue(specific().getTherapistClosingRequests().isEmpty());

		assertDoesNotThrow(() -> conversation.requestTherapistClosing(SPECIFIC, A, "x".repeat(800)));
	}

	@Test
	void shouldDeclareOnceOnlyAfterTheClosureWithAShortReason()
	{
		assertThrows(DomainException.class, () -> conversation.declareOutcome(SPECIFIC, A, Outcome.RESOLVED, "Too early."));

		conversation.requestClosing(SPECIFIC, A);
		conversation.acceptClosing(SPECIFIC, B);

		assertThrows(DomainException.class, () -> conversation.declareOutcome(SPECIFIC, A, Outcome.RESOLVED, ""));
		assertThrows(DomainException.class, () -> conversation.declareOutcome(SPECIFIC, A, Outcome.RESOLVED, "x".repeat(201)));
		assertThrows(DomainException.class, () -> conversation.declareOutcome(SPECIFIC, A, null, "Why not."));
		assertThrows(DomainException.class, () -> conversation.declareOutcome(SPECIFIC, THERAPIST, Outcome.RESOLVED, "Not mine."));

		conversation.declareOutcome(SPECIFIC, A, Outcome.RESOLVED, "x".repeat(200));
		assertThrows(DomainException.class, () -> conversation.declareOutcome(SPECIFIC, A, Outcome.STALLED, "Changed my mind."));
		assertEquals(1, specific().getClosure().get().declarations().size());
	}

	// NESTED DISCUSSIONS:

	@Test
	void shouldNotCloseNestedDiscussionsAndStillLetAClosedOneBeExpanded()
	{
		SimpleParagraph expanded = paragraph(1L);
		SimpleParagraph later = paragraph(2L);
		conversation.sendMessage(message(1L, A, expanded, later), SPECIFIC, NOON);
		conversation.openDiscussion(discussion(80L), expanded.getId());
		DiscussionId nested = new DiscussionId(Ids.conversation(321L), 2);

		conversation.requestClosing(SPECIFIC, A);
		conversation.acceptClosing(SPECIFIC, B);

		assertFalse(conversation.findDiscussion(nested).isClosed());
		assertDoesNotThrow(() -> conversation.openDiscussion(discussion(90L), later.getId()));
	}

	@Test
	void shouldNotLetTheClosingHistoryBeChangedFromOutside()
	{
		conversation.requestClosing(SPECIFIC, A);
		conversation.refuseClosing(SPECIFIC, B);
		conversation.requestTherapistClosing(SPECIFIC, A, "B never wants to close.");
		conversation.confirmTherapistClosing(SPECIFIC, THERAPIST);

		assertThrows(UnsupportedOperationException.class, () -> specific().getClosingRequests().clear());
		assertThrows(UnsupportedOperationException.class, () -> specific().getTherapistClosingRequests().clear());
		assertThrows(UnsupportedOperationException.class, () -> specific().getTherapistClosingRequests().getFirst().previousRequests().clear());
		assertThrows(UnsupportedOperationException.class, () -> specific().getClosure().get().declarations().clear());
	}
}
