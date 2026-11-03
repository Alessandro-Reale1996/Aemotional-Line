package com.aemotionalline.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import com.aemotionalline.CapturingMailer;
import com.aemotionalline.CapturingMailer.Kind;
import com.aemotionalline.TestcontainersConfiguration;
import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.conversation.ConversationId;
import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.couple.CoupleInvitationId;
import com.aemotionalline.domain.couple.CoupleInvitationRepository;
import com.aemotionalline.domain.couple.CoupleInvitationStatus;
import com.aemotionalline.domain.couple.CoupleRepository;
import com.aemotionalline.domain.message.ParagraphId;
import com.aemotionalline.domain.message.ParagraphType;
import com.aemotionalline.domain.negotiation.NegotiationId;
import com.aemotionalline.domain.user.EmailAddress;
import com.aemotionalline.domain.user.Role;
import com.aemotionalline.domain.user.UserId;
import com.aemotionalline.domain.user.UserRepository;

/**
 * Two write operations at the same moment (CLAUDE.md, 2026-11-03, the snapshot problem). Each test holds a lock in a
 * transaction of its own, lets the competing operations queue up behind it, then releases it: the second operation must
 * see what the first one committed, and the domain rules decide.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public class ConcurrentWritesTest
{
	private static final LocalTime NOON = LocalTime.NOON;

	@Autowired
	private InvitationService invitationService;

	@Autowired
	private NegotiationService negotiationService;

	@Autowired
	private ConversationService conversationService;

	@Autowired
	private ClosingService closingService;

	@Autowired
	private UserService userService;

	@Autowired
	private UserRepository users;

	@Autowired
	private CoupleRepository couples;

	@Autowired
	private CoupleInvitationRepository invitations;

	@Autowired
	private CapturingMailer mailer;

	@Autowired
	private TransactionTemplate transactions;

	@Test
	void shouldCreateTheCoupleWhenBothPartnersAcceptAtTheSameMoment() throws Exception
	{
		UserId therapist = account(Role.THERAPIST);
		UserId a = account(Role.PATIENT);
		UserId b = account(Role.PATIENT);
		CoupleInvitationId invitation = invitationService.invite(therapist, emailOf(a), "A", emailOf(b), "B");

		List<Throwable> outcomes = race(() -> invitations.findByIdForUpdate(invitation),
				() -> invitationService.accept(a, invitation),
				() -> invitationService.accept(b, invitation));

		assertEquals(List.of(), failures(outcomes));
		assertEquals(CoupleInvitationStatus.ACCEPTED, invitations.findById(invitation).orElseThrow().getStatus());
		assertTrue(couples.findActiveCoupleOf(a).orElseThrow().isPartner(b));
	}

	@Test
	void shouldKeepAPatientInOneCoupleWhenTwoInvitationsAreAcceptedAtTheSameMoment() throws Exception
	{
		UserId therapist = account(Role.THERAPIST);
		UserId a = account(Role.PATIENT);
		UserId b = account(Role.PATIENT);
		UserId c = account(Role.PATIENT);
		CoupleInvitationId withA = invitationService.invite(therapist, emailOf(a), "A", emailOf(b), "B");
		CoupleInvitationId withC = invitationService.invite(therapist, emailOf(b), "B", emailOf(c), "C");
		invitationService.accept(b, withA);
		invitationService.accept(c, withC);

		// A completes the first invitation while B completes the second: B can't end up in two couples.
		List<Throwable> outcomes = race(() ->
		{
			invitations.findByIdForUpdate(withA);
			invitations.findByIdForUpdate(withC);
		},
				() -> invitationService.accept(a, withA),
				() -> invitationService.accept(b, withC));

		assertEquals(1, failures(outcomes).size());
		assertTrue(failures(outcomes).getFirst() instanceof DomainException);
		assertTrue(couples.findActiveCoupleOf(b).isPresent());
		assertTrue(couples.findActiveCoupleOf(a).isPresent() != couples.findActiveCoupleOf(c).isPresent());
	}

	@Test
	void shouldNotLetAnAbandonedNegotiationUndoAClosingAcceptedMeanwhile() throws Exception
	{
		Started started = startedConversation();
		NegotiationId branch = negotiationService.startDiscussionNegotiation(started.a(), started.conversation(), "Holidays", "Where", Optional.empty());
		negotiationService.acceptRequest(started.b(), branch);
		negotiationService.sendProposal(started.a(), branch, "READY");
		negotiationService.acceptProposal(started.b(), branch);
		closingService.requestClosing(started.a(), started.conversation(), 1);
		NegotiationId other = negotiationService.startDiscussionNegotiation(started.a(), started.conversation(), "Work", "Hours", Optional.empty());

		// B accepts the closing (not yet committed) while A abandons another request, which is recorded in the conversation.
		List<Throwable> outcomes = race(() -> closingService.acceptClosing(started.b(), started.conversation(), 1),
				() -> negotiationService.abandon(started.a(), other));

		assertEquals(List.of(), failures(outcomes));
		assertTrue(closingService.view(started.therapist(), started.conversation(), 1).closed());
		assertEquals(List.of(), negotiationService.inProgress(started.a(), started.couple()));
	}

	@Test
	void shouldOpenOnlyOneRequestPerParagraphWhenTwoArriveAtTheSameMoment() throws Exception
	{
		Started started = startedConversation();
		ParagraphId origin = conversationService.view(started.a(), started.conversation()).discussions().getFirst().messages().getFirst()
				.paragraphs().getFirst().id();

		List<Throwable> outcomes = race(() -> couples.findByIdForUpdate(started.couple()),
				() -> negotiationService.startDiscussionNegotiation(started.a(), started.conversation(), "Holidays", "Where", Optional.of(origin)),
				() -> negotiationService.startDiscussionNegotiation(started.a(), started.conversation(), "Holidays", "Where", Optional.of(origin)));

		assertEquals(1, failures(outcomes).size());
		assertTrue(failures(outcomes).getFirst() instanceof DomainException);
		assertEquals(1, negotiationService.inProgress(started.a(), started.couple()).size());
	}

	@Test
	void shouldUseAResetLinkOnlyOnceWhenItIsUsedTwiceAtTheSameMoment() throws Exception
	{
		UserId user = account(Role.PATIENT);
		userService.requestPasswordReset(emailOf(user));
		String code = mailer.lastCode(emailOf(user), Kind.PASSWORD_RESET).orElseThrow();

		List<Throwable> outcomes = race(() -> users.findByIdForUpdate(user),
				() -> userService.resetPassword(code, "una password nuova numero uno"),
				() -> userService.resetPassword(code, "una password nuova numero due"));

		assertEquals(1, failures(outcomes).size());
		assertTrue(failures(outcomes).getFirst() instanceof InvalidRequestException);
	}

	// HELPERS

	private record Started(UserId therapist, UserId a, UserId b, CoupleId couple, ConversationId conversation)
	{
	}

	// A conversation where A sent a first message.
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
		conversationService.sendMessage(a, conversation, 0, new MessageDraft(List.of(
				new ParagraphDraft(ParagraphType.SIMPLE, "Title", null, "Hello.", List.of(), List.of(), Optional.empty()))), NOON, null);

		return new Started(therapist, a, b, couple, conversation);
	}

	/**
	 * Takes a lock (and possibly a change) in a transaction of its own and holds it while the operations start, each in
	 * its own thread and transaction; once they are all waiting, commits and lets them go. Returns what each one threw,
	 * or null when it succeeded.
	 */
	private List<Throwable> race(Runnable holder, Runnable... operations) throws Exception
	{
		CountDownLatch held = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(operations.length + 1);

		try
		{
			Future<?> hold = executor.submit(() -> transactions.executeWithoutResult(status ->
			{
				holder.run();
				held.countDown();
				await(release);
			}));
			assertTrue(held.await(10, TimeUnit.SECONDS));

			List<Future<Throwable>> racing = new ArrayList<>();

			for (Runnable operation : operations)
			{
				racing.add(executor.submit(() ->
				{
					try
					{
						operation.run();
						return null;
					}
					catch (Throwable failure)
					{
						return failure;
					}
				}));
			}

			Thread.sleep(500);
			racing.forEach(future -> assertFalse(future.isDone(), "an operation didn't wait for the lock"));

			release.countDown();
			assertNull(hold.get(10, TimeUnit.SECONDS));

			List<Throwable> outcomes = new ArrayList<>();

			for (Future<Throwable> future : racing)
			{
				outcomes.add(future.get(10, TimeUnit.SECONDS));
			}

			return outcomes;
		}
		finally
		{
			executor.shutdownNow();
		}
	}

	private static List<Throwable> failures(List<Throwable> outcomes)
	{
		return outcomes.stream().filter(Objects::nonNull).toList();
	}

	private UserId account(Role role)
	{
		UserId user = UserId.random();
		TestCouples.activeAccount(users, user, role);

		return user;
	}

	private EmailAddress emailOf(UserId user)
	{
		return users.findById(user).orElseThrow().getEmail();
	}

	private static void await(CountDownLatch latch)
	{
		try
		{
			latch.await(10, TimeUnit.SECONDS);
		}
		catch (InterruptedException interrupted)
		{
			Thread.currentThread().interrupt();
		}
	}
}
