package com.aemotionalline.application;

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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.aemotionalline.TestcontainersConfiguration;
import com.aemotionalline.application.CoupleDeletion.Cause;
import com.aemotionalline.application.view.EndedCoupleView;
import com.aemotionalline.domain.MutableClock;
import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.common.ErrorCode;
import com.aemotionalline.domain.conversation.ConversationId;
import com.aemotionalline.domain.conversation.ConversationRepository;
import com.aemotionalline.domain.conversation.FullConversation;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.couple.CoupleInvitation;
import com.aemotionalline.domain.couple.CoupleInvitationId;
import com.aemotionalline.domain.couple.CoupleInvitationRepository;
import com.aemotionalline.domain.couple.CoupleRepository;
import com.aemotionalline.domain.message.MessageId;
import com.aemotionalline.domain.message.ParagraphType;
import com.aemotionalline.domain.negotiation.AgreementText;
import com.aemotionalline.domain.negotiation.NegotiationId;
import com.aemotionalline.domain.user.EmailAddress;
import com.aemotionalline.domain.user.Role;
import com.aemotionalline.domain.user.User;
import com.aemotionalline.domain.user.UserId;
import com.aemotionalline.domain.user.UserRepository;

/** Ending a couple and deleting its data, end to end on MariaDB. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public class CoupleServiceTest
{
	private static final LocalTime NOON = LocalTime.NOON;

	// Tables that hold accounts, login sessions or the deletion's trace, not a couple's data.
	private static final Set<String> NOT_COUPLE_DATA = Set.of("flyway_schema_history", "user_account", "one_time_link", "deleted_couple",
			"SPRING_SESSION", "SPRING_SESSION_ATTRIBUTES");

	@Autowired
	private CoupleService coupleService;

	@Autowired
	private NegotiationService negotiationService;

	@Autowired
	private ConversationService conversationService;

	@Autowired
	private InvitationService invitationService;

	@Autowired
	private UserService userService;

	@Autowired
	private UserRepository users;

	@Autowired
	private CoupleRepository couples;

	@Autowired
	private CoupleInvitationRepository invitations;

	@Autowired
	private ConversationRepository conversations;

	@Autowired
	private CoupleErasure erasure;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private Clock clock;

	// ENDING

	@Test
	void shouldEndTheCoupleFreezeEverythingButStillLetMessagesBeOpened()
	{
		Started started = startedConversation();
		MessageId first = send(started.a(), started);
		NegotiationId inProgress = negotiationService.startDiscussionNegotiation(started.a(), started.conversation(), "Holidays", "Where", Optional.empty());
		negotiationService.acceptRequest(started.b(), inProgress);

		coupleService.end(started.therapist(), started.couple(), TestCouples.PASSWORD);

		assertFalse(couples.findById(started.couple()).orElseThrow().isActive());
		assertThrows(DomainException.class, () -> send(started.b(), started));
		assertThrows(DomainException.class, () -> negotiationService.sendProposal(started.a(), inProgress, "Ready"));
		assertThrows(DomainException.class, () -> negotiationService.editProposal(started.a(), inProgress, new AgreementText("Another")));
		assertThrows(DomainException.class, () -> negotiationService.abandon(started.b(), inProgress));
		assertThrows(DomainException.class, () -> negotiationService.startAgreementNegotiation(started.a(), started.couple(), "AGAIN"));
		assertEquals("Hello.", conversationService.readMessage(started.b(), started.conversation(), 0, first, NOON, null).paragraphs().getFirst().body());

		// Each patient can now be invited into a new couple.
		UserId otherTherapist = UserId.random();
		TestCouples.activeAccount(users, otherTherapist, Role.THERAPIST);
		assertDoesNotThrow(() -> invitationService.invite(otherTherapist, emailOf(started.a()), "A", emailOf(started.b()), "B"));
	}

	@Test
	void shouldLetOnlyTheCouplesTherapistEndItOnce()
	{
		UserId therapist = UserId.random();
		UserId a = UserId.random();
		CoupleId couple = TestCouples.create(users, couples, therapist, a, UserId.random());
		UserId otherTherapist = UserId.random();
		TestCouples.activeAccount(users, otherTherapist, Role.THERAPIST);

		assertThrows(DomainException.class, () -> coupleService.end(a, couple, TestCouples.PASSWORD));
		assertThrows(NotFoundException.class, () -> coupleService.end(otherTherapist, couple, TestCouples.PASSWORD));
		assertThrows(NotFoundException.class, () -> coupleService.end(therapist, CoupleId.random(), TestCouples.PASSWORD));
		assertTrue(couples.findById(couple).orElseThrow().isActive());

		coupleService.end(therapist, couple, TestCouples.PASSWORD);
		assertThrows(DomainException.class, () -> coupleService.end(therapist, couple, TestCouples.PASSWORD));
	}

	@Test
	void shouldEndTheCoupleOnlyWithTheTherapistsPassword()
	{
		UserId therapist = UserId.random();
		CoupleId couple = TestCouples.create(users, couples, therapist, UserId.random(), UserId.random());

		assertEquals(RequestErrorCode.INCORRECT_PASSWORD,
				assertThrows(IncorrectPasswordException.class, () -> coupleService.end(therapist, couple, "not the password")).getCode());

		assertTrue(couples.findById(couple).orElseThrow().isActive());
		assertEquals(1, users.findById(therapist).orElseThrow().getFailedLogins());
	}

	// DELETING

	@Test
	void shouldDeleteEveryPieceOfTheCouplesDataAndKeepOnlyTheTrace()
	{
		Map<String, Long> before = rowCounts();
		Full full = fullCouple();
		Map<String, Long> filled = rowCounts();

		// The couple has rows in every table of couple data, so the check below leaves none out.
		before.forEach((table, count) -> assertTrue(filled.get(table) > count, "no rows of the couple in " + table));

		coupleService.end(full.therapist(), full.couple(), TestCouples.PASSWORD);
		Instant endedAt = couples.findById(full.couple()).orElseThrow().getEndedAt().orElseThrow();
		coupleService.deleteData(full.therapist(), full.couple(), TestCouples.PASSWORD);

		assertEquals(before, rowCounts());
		CoupleDeletion trace = erasure.findDeletion(full.couple()).orElseThrow();
		assertEquals(full.therapist(), trace.therapist());
		assertEquals(endedAt, trace.endedAt());
		assertEquals(Cause.THERAPIST, trace.cause());
		assertEquals(Optional.of(full.therapist()), trace.deletedBy());
		assertTrue(couples.findById(full.couple()).isEmpty());
		assertTrue(conversations.findById(full.conversation()).isEmpty());
		assertThrows(NotFoundException.class, () -> conversationService.view(full.a(), full.conversation()));
		// The patients' accounts stay.
		assertTrue(users.findById(full.a()).isPresent());
		assertTrue(users.findById(full.b()).isPresent());
	}

	@Test
	void shouldNotDeleteTheDataOfAnActiveCouple()
	{
		UserId therapist = UserId.random();
		CoupleId couple = TestCouples.create(users, couples, therapist, UserId.random(), UserId.random());

		assertEquals(ErrorCode.COUPLE_NOT_ENDED,
				assertThrows(DomainException.class, () -> coupleService.deleteData(therapist, couple, TestCouples.PASSWORD)).getCode());
		assertTrue(couples.findById(couple).isPresent());
	}

	@Test
	void shouldLetOnlyTheCouplesTherapistDeleteTheData()
	{
		UserId therapist = UserId.random();
		UserId a = UserId.random();
		CoupleId couple = TestCouples.create(users, couples, therapist, a, UserId.random());
		coupleService.end(therapist, couple, TestCouples.PASSWORD);
		UserId otherTherapist = UserId.random();
		TestCouples.activeAccount(users, otherTherapist, Role.THERAPIST);
		UserId administrator = UserId.random();
		TestCouples.activeAccount(users, administrator, Role.ADMINISTRATOR);

		assertThrows(NotFoundException.class, () -> coupleService.deleteData(a, couple, TestCouples.PASSWORD));
		assertThrows(NotFoundException.class, () -> coupleService.deleteData(otherTherapist, couple, TestCouples.PASSWORD));
		// The therapist's account is active: the administrator has nothing to do with the couple.
		assertThrows(NotFoundException.class, () -> coupleService.deleteData(administrator, couple, TestCouples.PASSWORD));
		assertThrows(NotFoundException.class, () -> coupleService.deleteData(UserId.random(), couple, TestCouples.PASSWORD));
		assertTrue(couples.findById(couple).isPresent());
	}

	@Test
	void shouldCountAWrongPasswordAsAFailedLoginAndLockTheAccountAfterFive()
	{
		UserId therapist = UserId.random();
		CoupleId couple = TestCouples.create(users, couples, therapist, UserId.random(), UserId.random());
		coupleService.end(therapist, couple, TestCouples.PASSWORD);

		assertEquals(RequestErrorCode.INCORRECT_PASSWORD,
				assertThrows(IncorrectPasswordException.class, () -> coupleService.deleteData(therapist, couple, "not the password")).getCode());
		assertEquals(1, users.findById(therapist).orElseThrow().getFailedLogins());

		for (int attempt = 2; attempt <= User.MAX_FAILED_LOGINS; attempt++)
		{
			assertThrows(IncorrectPasswordException.class, () -> coupleService.deleteData(therapist, couple, null));
		}

		assertTrue(users.findById(therapist).orElseThrow().isLockedAt(clock.instant()));
		// Locked: even the right password is refused, with the same answer.
		assertThrows(IncorrectPasswordException.class, () -> coupleService.deleteData(therapist, couple, TestCouples.PASSWORD));
		assertTrue(couples.findById(couple).isPresent());
	}

	@Test
	void shouldClearTheFailuresOnceTheRightPasswordIsGiven()
	{
		UserId therapist = UserId.random();
		CoupleId couple = TestCouples.create(users, couples, therapist, UserId.random(), UserId.random());
		coupleService.end(therapist, couple, TestCouples.PASSWORD);

		assertThrows(IncorrectPasswordException.class, () -> coupleService.deleteData(therapist, couple, "x".repeat(100)));
		coupleService.deleteData(therapist, couple, TestCouples.PASSWORD);

		assertEquals(0, users.findById(therapist).orElseThrow().getFailedLogins());
		assertTrue(couples.findById(couple).isEmpty());
	}

	@Test
	void shouldLetAnAdministratorDeleteTheDataOfADisabledTherapistsCouple()
	{
		UserId therapist = UserId.random();
		CoupleId couple = TestCouples.create(users, couples, therapist, UserId.random(), UserId.random());
		UserId administrator = UserId.random();
		TestCouples.activeAccount(users, administrator, Role.ADMINISTRATOR);

		// While the therapist is active, the administrator sees nothing of their couples.
		assertThrows(NotFoundException.class, () -> coupleService.endedCouplesOfDisabledTherapist(administrator, therapist));

		// Disabling the therapist ends the couple: the administrator finds it, by id and end date only.
		userService.disableTherapist(administrator, therapist, TestCouples.PASSWORD);
		List<EndedCoupleView> ended = coupleService.endedCouplesOfDisabledTherapist(administrator, therapist);
		assertEquals(List.of(couple), ended.stream().map(EndedCoupleView::id).toList());
		assertEquals(couples.findById(couple).orElseThrow().getEndedAt().orElseThrow(), ended.getFirst().endedAt());
		assertThrows(NotFoundException.class, () -> coupleService.endedCouplesOfDisabledTherapist(therapist, therapist));

		coupleService.deleteData(administrator, couple, TestCouples.PASSWORD);
		assertEquals(List.of(), coupleService.endedCouplesOfDisabledTherapist(administrator, therapist));

		CoupleDeletion trace = erasure.findDeletion(couple).orElseThrow();
		assertEquals(Cause.ADMINISTRATOR, trace.cause());
		assertEquals(Optional.of(administrator), trace.deletedBy());
		assertEquals(therapist, trace.therapist());
		assertTrue(couples.findById(couple).isEmpty());
	}

	// HELPERS

	private record Started(UserId therapist, UserId a, UserId b, CoupleId couple, ConversationId conversation)
	{
	}

	private record Full(UserId therapist, UserId a, UserId b, CoupleId couple, ConversationId conversation)
	{
	}

	private Started startedConversation()
	{
		UserId therapist = UserId.random();
		UserId a = UserId.random();
		UserId b = UserId.random();
		CoupleId couple = TestCouples.create(users, couples, therapist, a, b);

		NegotiationId start = negotiationService.startAgreementNegotiation(a, couple, "OUR AGREEMENT");
		negotiationService.acceptRequest(b, start);
		negotiationService.sendProposal(a, start, "READY");
		ConversationId conversation = negotiationService.acceptProposal(b, start).conversationId();

		return new Started(therapist, a, b, couple, conversation);
	}

	private MessageId send(UserId sender, Started started)
	{
		return conversationService.sendMessage(sender, started.conversation(), 0, new MessageDraft(List.of(
				new ParagraphDraft(ParagraphType.SIMPLE, "Title", null, "Hello.", List.of(), List.of(), Optional.empty()))), NOON, null);
	}

	// A couple born from an invitation, with the full conversation (every kind of message, link, request, record and
	// constraint) and a negotiation in progress: it has rows in every table of couple data.
	private Full fullCouple()
	{
		UserId therapist = UserId.random();
		UserId a = UserId.random();
		UserId b = UserId.random();
		TestCouples.activeAccount(users, therapist, Role.THERAPIST);
		TestCouples.activeAccount(users, a, Role.PATIENT);
		TestCouples.activeAccount(users, b, Role.PATIENT);

		Instant now = clock.instant();
		CoupleInvitation invitation = CoupleInvitation.create(CoupleInvitationId.random(), therapist, a, b, now, now.plus(Duration.ofDays(7)));
		invitation.accept(a, now);
		Couple couple = invitation.accept(b, now).orElseThrow();
		couples.save(couple);
		invitations.save(invitation);

		FullConversation full = new FullConversation(couple, ConversationId.random(), new MutableClock(Instant.parse("2026-10-30T10:00:00Z")));
		conversations.save(full.conversation);

		NegotiationId inProgress = negotiationService.startDiscussionNegotiation(a, full.conversation.getId(), "Holidays", "Where", Optional.empty());
		negotiationService.acceptRequest(b, inProgress);

		return new Full(therapist, a, b, couple.getId(), full.conversation.getId());
	}

	private Map<String, Long> rowCounts()
	{
		Map<String, Long> counts = new TreeMap<>();

		for (String table : jdbc.sql("SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()").query(String.class).list())
		{
			if (!NOT_COUPLE_DATA.contains(table))
			{
				counts.put(table, jdbc.sql("SELECT COUNT(*) FROM " + table).query(Long.class).single());
			}
		}

		return counts;
	}

	private EmailAddress emailOf(UserId user)
	{
		return users.findById(user).orElseThrow().getEmail();
	}
}
