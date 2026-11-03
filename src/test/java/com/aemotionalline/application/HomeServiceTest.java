package com.aemotionalline.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.aemotionalline.TestcontainersConfiguration;
import com.aemotionalline.application.view.CoupleView;
import com.aemotionalline.application.view.SummaryView;
import com.aemotionalline.application.view.SummaryView.CoupleSummary;
import com.aemotionalline.application.view.SummaryView.UnopenedMessages;
import com.aemotionalline.domain.constraint.ConstraintChangeRequestId;
import com.aemotionalline.domain.conversation.ConversationId;
import com.aemotionalline.domain.conversation.DiscussionId;
import com.aemotionalline.domain.conversation.NegotiationRecord.Purpose;
import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.couple.CoupleInvitationId;
import com.aemotionalline.domain.couple.CoupleRepository;
import com.aemotionalline.domain.message.ParagraphType;
import com.aemotionalline.domain.negotiation.NegotiationId;
import com.aemotionalline.domain.user.EmailAddress;
import com.aemotionalline.domain.user.Role;
import com.aemotionalline.domain.user.UserId;
import com.aemotionalline.domain.user.UserRepository;

/** Where the frontend starts (CLAUDE.md, R3), on MariaDB: the user's couples and what waits for each member. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public class HomeServiceTest
{
	private static final LocalTime NOON = LocalTime.NOON;

	@Autowired
	private HomeService home;

	@Autowired
	private NegotiationService negotiationService;

	@Autowired
	private ConversationService conversationService;

	@Autowired
	private ClosingService closingService;

	@Autowired
	private ConstraintService constraintService;

	@Autowired
	private CoupleService coupleService;

	@Autowired
	private InvitationService invitationService;

	@Autowired
	private UserService userService;

	@Autowired
	private UserRepository users;

	@Autowired
	private CoupleRepository couples;

	@Test
	void shouldListTheUsersCouplesActiveFirstWithTheirMembersNames()
	{
		UserId therapist = UserId.random();
		UserId a = UserId.random();
		UserId b = UserId.random();
		CoupleId ended = TestCouples.create(users, couples, therapist, a, b);
		coupleService.end(therapist, ended, TestCouples.PASSWORD);
		UserId c = UserId.random();
		TestCouples.activeAccount(users, c, Role.PATIENT);
		CoupleId active = createCoupleWithExistingPatients(therapist, a, c);
		ConversationId conversation = startConversation(a, c, active);

		List<CoupleView> ofA = home.couples(a);
		assertEquals(List.of(active, ended), ofA.stream().map(CoupleView::id).toList());
		assertTrue(ofA.getFirst().active());
		assertEquals(Optional.of(conversation), ofA.getFirst().conversation());
		assertFalse(ofA.get(1).active());
		assertTrue(ofA.get(1).endedAt().isPresent());
		assertEquals(Optional.empty(), ofA.get(1).conversation());
		assertEquals("therapist", ofA.getFirst().therapist().displayName());

		assertEquals(2, home.couples(therapist).size());
		assertEquals(List.of(), home.couples(UserId.random()));

		// B deletes their account after the end: their name is gone, the couple stays.
		userService.deleteOwnPatientAccount(b, TestCouples.PASSWORD);
		CoupleView seenByTherapist = home.couples(therapist).stream().filter(couple -> couple.id().equals(ended)).findFirst().orElseThrow();
		assertNull(seenByTherapist.partnerTwo().displayName());
		assertEquals(b, seenByTherapist.partnerTwo().id());
	}

	@Test
	void shouldSummariseWhatWaitsForEachMember()
	{
		UserId therapist = UserId.random();
		UserId a = UserId.random();
		UserId b = UserId.random();
		CoupleId couple = TestCouples.create(users, couples, therapist, a, b);
		ConversationId conversation = startConversation(a, b, couple);

		// A writes in the main discussion; B hasn't opened it.
		send(a, conversation, 0);
		// A asks to open a discussion: B has to answer.
		NegotiationId request = negotiationService.startDiscussionNegotiation(a, conversation, "Holidays", "Where", Optional.empty());
		// The therapist proposes constraints: both partners have to decide; A approves.
		ConstraintChangeRequestId constraints = constraintService.proposeConstraints(therapist, conversation,
				List.of(new ConstraintSpec(a, "TIME", null, LocalTime.of(9, 0), null, null, null, null, null)));
		constraintService.approveConstraints(a, conversation, constraints);

		CoupleSummary ofB = only(home.summary(b));
		assertEquals(Optional.of(conversation), ofB.conversation());
		assertEquals(List.of(new SummaryView.NegotiationWaiting(request, Purpose.OPENED_DISCUSSION)), ofB.negotiationsWaitingForYou());
		assertEquals(List.of(new UnopenedMessages(new DiscussionId(conversation, 0), 1)), ofB.unopenedMessages());
		assertEquals(List.of(constraints), ofB.constraintRequestsToDecide());

		CoupleSummary ofA = only(home.summary(a));
		assertEquals(List.of(), ofA.negotiationsWaitingForYou());
		assertEquals(List.of(), ofA.unopenedMessages());
		assertEquals(List.of(), ofA.constraintRequestsToDecide());

		// The discussion opens; A asks to close it, B refuses, A turns to the therapist.
		negotiationService.acceptRequest(b, request);
		negotiationService.sendProposal(a, request, "READY");
		negotiationService.acceptProposal(b, request);
		closingService.requestClosing(a, conversation, 1);
		assertEquals(List.of(new DiscussionId(conversation, 1)), only(home.summary(b)).closingRequestsToAnswer());
		assertEquals(List.of(), only(home.summary(a)).closingRequestsToAnswer());
		closingService.refuseClosing(b, conversation, 1);
		closingService.requestTherapistClosing(a, conversation, 1, "B refuses every time.");

		CoupleSummary ofTherapist = only(home.summary(therapist));
		assertEquals(List.of(new DiscussionId(conversation, 1)), ofTherapist.therapistRequestsToDecide());
		assertEquals(List.of(), ofTherapist.unopenedMessages());

		// Once B opens the message, nothing is unopened any more.
		conversationService.readMessage(b, conversation, 0,
				conversationService.view(b, conversation).discussions().getFirst().messages().getFirst().id(), NOON, null);
		assertEquals(List.of(), only(home.summary(b)).unopenedMessages());

		// An ended couple leaves nothing to do.
		coupleService.end(therapist, couple, TestCouples.PASSWORD);
		assertEquals(List.of(), home.summary(b).couples());
	}

	@Test
	void shouldListTheInvitationsWaitingForAnAnswer()
	{
		UserId therapist = UserId.random();
		TestCouples.activeAccount(users, therapist, Role.THERAPIST);
		UserId c = UserId.random();
		TestCouples.activeAccount(users, c, Role.PATIENT);

		CoupleInvitationId invitation = invitationService.invite(therapist, users.findById(c).orElseThrow().getEmail(), "C",
				new EmailAddress(UUID.randomUUID() + "@example.org"), "D");

		assertEquals(List.of(invitation), home.summary(c).pendingInvitations().stream().map(view -> view.id()).toList());
		assertTrue(home.summary(therapist).pendingInvitations().stream().anyMatch(view -> view.id().equals(invitation)));
	}

	private CoupleId createCoupleWithExistingPatients(UserId therapist, UserId one, UserId two)
	{
		CoupleInvitationId invitation = invitationService.invite(therapist, users.findById(one).orElseThrow().getEmail(), "A",
				users.findById(two).orElseThrow().getEmail(), "C");
		invitationService.accept(one, invitation);
		invitationService.accept(two, invitation);

		return invitationService.view(one, invitation).coupleId().orElseThrow();
	}

	private ConversationId startConversation(UserId a, UserId b, CoupleId couple)
	{
		NegotiationId start = negotiationService.startAgreementNegotiation(a, couple, "OUR AGREEMENT");
		negotiationService.acceptRequest(b, start);
		negotiationService.sendProposal(a, start, "READY");

		return negotiationService.acceptProposal(b, start).conversationId();
	}

	private void send(UserId sender, ConversationId conversation, int discussion)
	{
		conversationService.sendMessage(sender, conversation, discussion, new MessageDraft(List.of(
				new ParagraphDraft(ParagraphType.SIMPLE, "Title", null, "Hello.", List.of(), List.of(), Optional.empty()))), NOON, null);
	}

	private static CoupleSummary only(SummaryView summary)
	{
		assertEquals(1, summary.couples().size());

		return summary.couples().getFirst();
	}
}
