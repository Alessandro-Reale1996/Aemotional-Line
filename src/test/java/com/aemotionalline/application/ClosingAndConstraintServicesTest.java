package com.aemotionalline.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.aemotionalline.TestcontainersConfiguration;
import com.aemotionalline.application.view.ClosingView;
import com.aemotionalline.application.view.ConstraintsView;
import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.constraint.ConstraintChangeRequestId;
import com.aemotionalline.domain.constraint.ConstraintChangeRequestStatus;
import com.aemotionalline.domain.constraint.ConstraintScope;
import com.aemotionalline.domain.constraint.LocationConstraint;
import com.aemotionalline.domain.conversation.ClosingRequestRecord;
import com.aemotionalline.domain.conversation.ConversationId;
import com.aemotionalline.domain.conversation.DiscussionClosure;
import com.aemotionalline.domain.conversation.OutcomeDeclaration.Outcome;
import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.message.ParagraphType;
import com.aemotionalline.domain.negotiation.NegotiationId;
import com.aemotionalline.domain.user.UserId;

/** Closing a specific discussion and changing the constraints, end to end on MariaDB, with who may see what. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public class ClosingAndConstraintServicesTest
{
	private static final int BRANCH = 1;

	@Autowired
	private ClosingService closing;

	@Autowired
	private ConstraintService constraints;

	@Autowired
	private ConversationService conversationService;

	@Autowired
	private NegotiationService negotiations;

	@Autowired
	private CoupleService coupleService;

	private UserId a;
	private UserId b;
	private UserId therapist;
	private ConversationId conversation;

	// A conversation with a specific discussion (number 1) opened without origin.
	@BeforeEach
	void openASpecificDiscussion()
	{
		a = UserId.random();
		b = UserId.random();
		therapist = UserId.random();
		CoupleId couple = coupleService.createCouple(therapist, a, b);

		NegotiationId start = negotiations.startAgreementNegotiation(a, couple, "OUR AGREEMENT");
		negotiations.acceptRequest(b, start);
		negotiations.sendProposal(a, start, "READY");
		conversation = negotiations.acceptProposal(b, start).conversationId();

		NegotiationId branch = negotiations.startDiscussionNegotiation(a, conversation, "Holidays", "Where and when", Optional.empty());
		negotiations.acceptRequest(b, branch);
		negotiations.sendProposal(a, branch, "LET'S TALK");
		negotiations.acceptProposal(b, branch);
	}

	// CLOSING

	@Test
	void shouldShowTheOtherDeclarationOnlyOnceBothHaveDeclared()
	{
		closing.requestClosing(a, conversation, BRANCH);
		closing.acceptClosing(b, conversation, BRANCH);
		closing.declareOutcome(a, conversation, BRANCH, Outcome.RESOLVED, "We agreed.");

		ClosingView seenByB = closing.view(b, conversation, BRANCH);
		assertTrue(seenByB.closed());
		assertEquals(Optional.of(DiscussionClosure.Way.AGREEMENT), seenByB.way());
		assertEquals(a, seenByB.declarations().getFirst().partner());
		assertTrue(seenByB.declarations().getFirst().outcome().isEmpty());
		assertTrue(seenByB.declarations().getFirst().reason().isEmpty());

		assertEquals(Optional.of(Outcome.RESOLVED), closing.view(a, conversation, BRANCH).declarations().getFirst().outcome());
		assertEquals(Optional.of("We agreed."), closing.view(therapist, conversation, BRANCH).declarations().getFirst().reason());

		closing.declareOutcome(b, conversation, BRANCH, Outcome.STALLED, "Not for me.");

		ClosingView afterBoth = closing.view(b, conversation, BRANCH);
		assertEquals(List.of(Optional.of(Outcome.RESOLVED), Optional.of(Outcome.STALLED)),
				afterBoth.declarations().stream().map(ClosingView.DeclarationView::outcome).toList());
	}

	@Test
	void shouldShowTheJustificationToTheTherapistOnlyToTheRequesterAndTheTherapist()
	{
		closing.requestClosing(a, conversation, BRANCH);
		closing.refuseClosing(b, conversation, BRANCH);
		closing.requestTherapistClosing(a, conversation, BRANCH, "B never wants to close.");

		ClosingView.TherapistRequestView seenByB = closing.view(b, conversation, BRANCH).therapistRequests().getFirst();
		assertEquals(a, seenByB.requester());
		assertTrue(seenByB.justification().isEmpty());
		assertEquals(List.of(ClosingRequestRecord.Status.REFUSED), seenByB.previousRequests().stream().map(ClosingRequestRecord::status).toList());

		assertEquals(Optional.of("B never wants to close."), closing.view(a, conversation, BRANCH).therapistRequests().getFirst().justification());
		assertEquals(Optional.of("B never wants to close."), closing.view(therapist, conversation, BRANCH).therapistRequests().getFirst().justification());

		assertThrows(DomainException.class, () -> closing.confirmTherapistClosing(b, conversation, BRANCH));
		closing.confirmTherapistClosing(therapist, conversation, BRANCH);
		assertEquals(Optional.of(DiscussionClosure.Way.THERAPIST), closing.view(b, conversation, BRANCH).way());
	}

	@Test
	void shouldApplyTheClosingRules()
	{
		assertThrows(DomainException.class, () -> closing.requestClosing(a, conversation, 0));
		assertThrows(DomainException.class, () -> closing.requestTherapistClosing(a, conversation, BRANCH, "Without trying first."));

		closing.requestClosing(a, conversation, BRANCH);
		assertThrows(DomainException.class, () -> closing.acceptClosing(a, conversation, BRANCH));
		closing.withdrawClosing(a, conversation, BRANCH);

		assertEquals(ClosingRequestRecord.Status.WITHDRAWN, closing.view(therapist, conversation, BRANCH).requests().getFirst().status());
		assertThrows(NotFoundException.class, () -> closing.requestClosing(UserId.random(), conversation, BRANCH));
		assertThrows(NotFoundException.class, () -> closing.view(UserId.random(), conversation, BRANCH));
	}

	@Test
	void shouldStopMessagesInAClosedDiscussion()
	{
		closing.requestClosing(a, conversation, BRANCH);
		closing.acceptClosing(b, conversation, BRANCH);

		MessageDraft draft = new MessageDraft(List.of(new ParagraphDraft(ParagraphType.SIMPLE, "Late", null, "Too late.", List.of(), List.of(), Optional.empty())));
		assertThrows(DomainException.class, () -> conversationService.sendMessage(a, conversation, BRANCH, draft, LocalTime.NOON, null));
	}

	// CONSTRAINTS

	private static ConstraintSpec notBefore(UserId user, int hour)
	{
		return new ConstraintSpec(user, "TIME", null, LocalTime.of(hour, 0), null, null, null, null, null);
	}

	@Test
	void shouldApplyTheConstraintsOnceBothPartnersApprove()
	{
		ConstraintChangeRequestId request = constraints.proposeConstraints(therapist, conversation, List.of(
				notBefore(a, 18),
				new ConstraintSpec(b, "REPLY_DELAY", null, null, Duration.ofHours(1), null, null, null, null),
				new ConstraintSpec(b, "LOCATION", null, null, null, 45.4642, 9.19, 200.0, LocationConstraint.Direction.WITHIN)));

		constraints.approveConstraints(a, conversation, request);
		assertTrue(constraints.view(b, conversation).inForce().isEmpty());
		constraints.approveConstraints(b, conversation, request);

		ConstraintsView view = constraints.view(b, conversation);
		assertEquals(List.of("TIME", "REPLY_DELAY", "LOCATION"), view.inForce().stream().map(constraint -> constraint.type()).toList());
		assertEquals(ConstraintScope.READ, view.inForce().get(2).scope());
		assertEquals(Duration.ofHours(1), view.inForce().get(1).delay());
		assertEquals(ConstraintChangeRequestStatus.APPLIED, view.requests().getFirst().status());
		assertEquals(Optional.of(List.of()), view.requests().getFirst().previousAssignments());

		// The time constraint now binds A: at noon A can't write.
		MessageDraft draft = new MessageDraft(List.of(new ParagraphDraft(ParagraphType.SIMPLE, "Hi", null, "Hello.", List.of(), List.of(), Optional.empty())));
		assertThrows(DomainException.class, () -> conversationService.sendMessage(a, conversation, 0, draft, LocalTime.NOON, null));
		conversationService.sendMessage(a, conversation, 0, draft, LocalTime.of(18, 30), null);
	}

	@Test
	void shouldKeepRejectedAndWithdrawnRequests()
	{
		ConstraintChangeRequestId rejected = constraints.proposeConstraints(therapist, conversation, List.of(notBefore(a, 9)));
		constraints.rejectConstraints(b, conversation, rejected);
		ConstraintChangeRequestId withdrawn = constraints.proposeConstraints(therapist, conversation, List.of(notBefore(b, 9)));
		constraints.withdrawConstraints(therapist, conversation, withdrawn);

		ConstraintsView view = constraints.view(a, conversation);
		assertEquals(List.of(ConstraintChangeRequestStatus.REJECTED, ConstraintChangeRequestStatus.WITHDRAWN),
				view.requests().stream().map(ConstraintsView.RequestView::status).toList());
		assertEquals(Optional.of(b), view.requests().getFirst().rejectedBy());
		assertTrue(view.inForce().isEmpty());
	}

	@Test
	void shouldRejectMalformedConstraintsAndUnauthorisedUsers()
	{
		assertThrows(InvalidRequestException.class, () -> constraints.proposeConstraints(therapist, conversation,
				List.of(new ConstraintSpec(a, "TIME", null, null, null, null, null, null, null))));
		assertThrows(InvalidRequestException.class, () -> constraints.proposeConstraints(therapist, conversation,
				List.of(new ConstraintSpec(a, "MOOD", null, null, null, null, null, null, null))));
		assertThrows(InvalidRequestException.class, () -> constraints.proposeConstraints(therapist, conversation,
				List.of(new ConstraintSpec(a, "REPLY_DELAY", ConstraintScope.READ, null, Duration.ofHours(1), null, null, null, null))));
		assertThrows(InvalidRequestException.class, () -> constraints.proposeConstraints(therapist, conversation, List.of(notBefore(null, 9))));

		assertThrows(DomainException.class, () -> constraints.proposeConstraints(a, conversation, List.of(notBefore(b, 9))));
		assertThrows(NotFoundException.class, () -> constraints.proposeConstraints(UserId.random(), conversation, List.of(notBefore(b, 9))));
		assertThrows(NotFoundException.class, () -> constraints.view(UserId.random(), conversation));
	}
}
