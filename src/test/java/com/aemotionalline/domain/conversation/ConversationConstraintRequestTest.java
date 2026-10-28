package com.aemotionalline.domain.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.Ids;
import com.aemotionalline.domain.MutableClock;
import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.constraint.ConstraintAssignment;
import com.aemotionalline.domain.constraint.ConstraintChangeRequestId;
import com.aemotionalline.domain.constraint.ConstraintChangeRequestStatus;
import com.aemotionalline.domain.constraint.ConstraintRequestRecord;
import com.aemotionalline.domain.constraint.TimeConstraint;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.negotiation.AgreementProposal;
import com.aemotionalline.domain.negotiation.Negotiation;
import com.aemotionalline.domain.user.UserId;

/**
 * The conversation owns the constraint change requests (spec 1.3.2): the therapist proposes, both partners must approve
 * before the constraints change, and every request stays in the history however it ends, with its instants.
 */
public class ConversationConstraintRequestTest
{
	private static final Couple COUPLE = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));

	private static final UserId A = COUPLE.getPartnerOneId();
	private static final UserId B = COUPLE.getPartnerTwoId();
	private static final UserId THERAPIST = COUPLE.getTherapistId();

	private static final Instant T0 = Instant.parse("2026-10-28T10:00:00Z");

	private final MutableClock clock = new MutableClock(T0);

	private Conversation startConversation()
	{
		Negotiation negotiation = Negotiation.start(Ids.negotiation(60L), COUPLE, new AgreementProposal(Ids.proposal(60L), A, "TEXT"), Clock.systemUTC());
		negotiation.acceptNegotiation(B);
		negotiation.sendProposal(A, "JUSTIFICATION TEXT");
		negotiation.acceptProposal(B);

		return Conversation.start(Ids.conversation(321L), COUPLE, negotiation, clock);
	}

	private static List<ConstraintAssignment> notBefore(UserId user, int hour)
	{
		return List.of(new ConstraintAssignment(user, new TimeConstraint(LocalTime.of(hour, 0))));
	}

	private static ConstraintRequestRecord only(Conversation conversation)
	{
		assertEquals(1, conversation.getConstraintRequestHistory().size());
		return conversation.getConstraintRequestHistory().getFirst();
	}

	@Test
	void shouldApplyTheConstraintsWhenTheSecondPartnerApproves()
	{
		Conversation conversation = startConversation();
		List<ConstraintAssignment> assignments = notBefore(A, 9);
		ConstraintChangeRequestId request = conversation.proposeConstraints(THERAPIST, assignments);

		clock.advance(Duration.ofHours(1));
		conversation.approveConstraints(request, A);
		assertTrue(conversation.getConstraintAssignments().isEmpty());

		clock.advance(Duration.ofHours(1));
		conversation.approveConstraints(request, B);

		assertEquals(assignments, conversation.getConstraintAssignments());
		ConstraintRequestRecord record = only(conversation);
		assertEquals(request, record.id());
		assertEquals(ConstraintChangeRequestStatus.APPLIED, record.status());
		assertEquals(Set.of(A, B), record.approvals());
		assertEquals(T0, record.proposedAt());
		assertEquals(Optional.of(T0.plus(Duration.ofHours(2))), record.closedAt());
		assertEquals(Optional.of(List.of()), record.previousAssignments());
	}

	@Test
	void shouldShowTheConstraintsBeforeAndAfterEachChange()
	{
		Conversation conversation = startConversation();
		ConstraintChangeRequestId first = conversation.proposeConstraints(THERAPIST, notBefore(A, 9));
		conversation.approveConstraints(first, A);
		conversation.approveConstraints(first, B);

		ConstraintChangeRequestId second = conversation.proposeConstraints(THERAPIST, notBefore(B, 18));
		conversation.approveConstraints(second, B);
		conversation.approveConstraints(second, A);

		List<ConstraintRequestRecord> history = conversation.getConstraintRequestHistory();
		assertEquals(2, history.size());
		assertEquals(Optional.of(history.get(0).assignments()), history.get(1).previousAssignments());
		assertEquals(history.get(1).assignments(), conversation.getConstraintAssignments());
	}

	@Test
	void shouldKeepARejectedRequestWithThePartnerWhoRejectedIt()
	{
		Conversation conversation = startConversation();
		ConstraintChangeRequestId request = conversation.proposeConstraints(THERAPIST, notBefore(A, 9));
		conversation.approveConstraints(request, A);

		clock.advance(Duration.ofMinutes(5));
		conversation.rejectConstraints(request, B);

		assertTrue(conversation.getConstraintAssignments().isEmpty());
		ConstraintRequestRecord record = only(conversation);
		assertEquals(ConstraintChangeRequestStatus.REJECTED, record.status());
		assertEquals(Optional.of(B), record.rejectedBy());
		assertEquals(Optional.of(T0.plus(Duration.ofMinutes(5))), record.closedAt());
		assertEquals(Optional.empty(), record.previousAssignments());
		assertThrows(DomainException.class, () -> conversation.approveConstraints(request, B));
	}

	@Test
	void shouldKeepAWithdrawnRequest()
	{
		Conversation conversation = startConversation();
		ConstraintChangeRequestId request = conversation.proposeConstraints(THERAPIST, notBefore(A, 9));

		assertThrows(DomainException.class, () -> conversation.withdrawConstraints(request, A));
		conversation.withdrawConstraints(request, THERAPIST);

		ConstraintRequestRecord record = only(conversation);
		assertEquals(ConstraintChangeRequestStatus.WITHDRAWN, record.status());
		assertEquals(Optional.empty(), record.rejectedBy());
		assertTrue(conversation.getConstraintAssignments().isEmpty());
	}

	@Test
	void shouldAllowOnlyOnePendingRequestAtATime()
	{
		Conversation conversation = startConversation();
		ConstraintChangeRequestId first = conversation.proposeConstraints(THERAPIST, notBefore(A, 9));

		assertThrows(DomainException.class, () -> conversation.proposeConstraints(THERAPIST, notBefore(B, 9)));
		assertEquals(1, conversation.getConstraintRequestHistory().size());

		conversation.rejectConstraints(first, A);

		conversation.proposeConstraints(THERAPIST, notBefore(B, 9));
		assertEquals(2, conversation.getConstraintRequestHistory().size());
	}

	@Test
	void shouldLetOnlyTheTherapistProposeAndOnlyThePartnersDecide()
	{
		Conversation conversation = startConversation();

		assertThrows(DomainException.class, () -> conversation.proposeConstraints(A, notBefore(A, 9)));
		assertTrue(conversation.getConstraintRequestHistory().isEmpty());

		ConstraintChangeRequestId request = conversation.proposeConstraints(THERAPIST, notBefore(A, 9));

		assertThrows(DomainException.class, () -> conversation.approveConstraints(request, THERAPIST));
		assertThrows(DomainException.class, () -> conversation.rejectConstraints(request, Ids.user(99L)));
		assertEquals(ConstraintChangeRequestStatus.PENDING, only(conversation).status());
	}

	@Test
	void shouldRejectAnUnknownRequest()
	{
		Conversation conversation = startConversation();
		ConstraintChangeRequestId unknown = ConstraintChangeRequestId.random();

		assertThrows(DomainException.class, () -> conversation.approveConstraints(unknown, A));
		assertThrows(DomainException.class, () -> conversation.rejectConstraints(unknown, A));
		assertThrows(DomainException.class, () -> conversation.withdrawConstraints(unknown, THERAPIST));
		assertThrows(DomainException.class, () -> conversation.approveConstraints(null, A));
	}

	@Test
	void shouldNotLetTheHistoryOrTheConstraintsBeChangedFromOutside()
	{
		Conversation conversation = startConversation();
		ConstraintChangeRequestId request = conversation.proposeConstraints(THERAPIST, notBefore(A, 9));
		conversation.approveConstraints(request, A);
		conversation.approveConstraints(request, B);

		assertThrows(UnsupportedOperationException.class, () -> conversation.getConstraintRequestHistory().clear());
		assertThrows(UnsupportedOperationException.class, () -> conversation.getConstraintAssignments().clear());
		assertEquals(1, conversation.getConstraintAssignments().size());
	}
}
