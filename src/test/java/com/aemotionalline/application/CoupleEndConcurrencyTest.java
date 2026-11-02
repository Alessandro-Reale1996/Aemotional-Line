package com.aemotionalline.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import com.aemotionalline.TestcontainersConfiguration;
import com.aemotionalline.application.view.MessageView;
import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.conversation.ConversationId;
import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.couple.CoupleRepository;
import com.aemotionalline.domain.message.MessageId;
import com.aemotionalline.domain.message.ParagraphType;
import com.aemotionalline.domain.negotiation.NegotiationId;
import com.aemotionalline.domain.user.UserId;
import com.aemotionalline.domain.user.UserRepository;

/**
 * The therapist ends the couple while a partner acts: the end waits for an operation already in progress, and an
 * operation that comes later waits for the end and then finds everything frozen. Nothing happens "after" the end.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public class CoupleEndConcurrencyTest
{
	private static final LocalTime NOON = LocalTime.NOON;

	@Autowired
	private CoupleService coupleService;

	@Autowired
	private NegotiationService negotiationService;

	@Autowired
	private ConversationService conversationService;

	@Autowired
	private UserRepository users;

	@Autowired
	private CoupleRepository couples;

	@Autowired
	private TransactionTemplate transactions;

	private UserId therapist;
	private UserId a;
	private UserId b;
	private CoupleId couple;
	private ConversationId conversation;
	private NegotiationId inProgress;

	// A conversation where A wrote and B opened it (B may answer), and a discussion request where it is A's turn to send.
	@BeforeEach
	void startAConversation()
	{
		therapist = UserId.random();
		a = UserId.random();
		b = UserId.random();
		couple = TestCouples.create(users, couples, therapist, a, b);

		NegotiationId start = negotiationService.startAgreementNegotiation(a, couple, "OUR AGREEMENT");
		negotiationService.acceptRequest(b, start);
		negotiationService.sendProposal(a, start, "READY");
		conversation = negotiationService.acceptProposal(b, start).conversationId();

		MessageId first = send(a);
		conversationService.readMessage(b, conversation, 0, first, NOON, null);

		inProgress = negotiationService.startDiscussionNegotiation(a, conversation, "Holidays", "Where", Optional.empty());
		negotiationService.acceptRequest(b, inProgress);
	}

	@Test
	void shouldWaitForAMessageBeingSentBeforeEnding() throws Exception
	{
		CountDownLatch sending = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);

		try
		{
			// B's message is being sent (the conversation is locked, not yet committed)...
			Future<MessageId> message = executor.submit(() -> transactions.execute(status ->
			{
				MessageId sent = send(b);
				sending.countDown();
				await(release);
				return sent;
			}));
			assertTrue(sending.await(10, TimeUnit.SECONDS));

			// ...when the therapist ends the couple: the end waits.
			Future<Throwable> end = executor.submit(failureOf(() -> coupleService.end(therapist, couple)));
			Thread.sleep(500);
			assertFalse(end.isDone());

			release.countDown();
			MessageId sent = message.get(10, TimeUnit.SECONDS);
			assertEquals(null, end.get(10, TimeUnit.SECONDS));

			Instant endedAt = couples.findById(couple).orElseThrow().getEndedAt().orElseThrow();
			MessageView view = conversationService.view(therapist, conversation).discussions().getFirst().messages().stream()
					.filter(candidate -> candidate.id().equals(sent)).findFirst().orElseThrow();
			assertTrue(view.sentAt().isBefore(endedAt));
		}
		finally
		{
			executor.shutdownNow();
		}
	}

	@Test
	void shouldMakeAMessageAndAProposalWaitForTheEndAndThenRefuseThem() throws Exception
	{
		CountDownLatch ending = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(3);

		try
		{
			// The therapist is ending the couple (its locks are held, not yet committed)...
			Future<?> end = executor.submit(() -> transactions.executeWithoutResult(status ->
			{
				coupleService.end(therapist, couple);
				ending.countDown();
				await(release);
			}));
			assertTrue(ending.await(10, TimeUnit.SECONDS));

			// ...while B answers and A sends a proposal: both wait.
			Future<Throwable> message = executor.submit(failureOf(() -> send(b)));
			Future<Throwable> proposal = executor.submit(failureOf(() -> negotiationService.sendProposal(a, inProgress, "READY")));
			Thread.sleep(500);
			assertFalse(message.isDone());
			assertFalse(proposal.isDone());

			release.countDown();
			end.get(10, TimeUnit.SECONDS);

			// They found the couple ended: the domain refused both, nothing was written.
			assertTrue(message.get(10, TimeUnit.SECONDS) instanceof DomainException);
			assertTrue(proposal.get(10, TimeUnit.SECONDS) instanceof DomainException);
			assertEquals(1, conversationService.view(therapist, conversation).discussions().getFirst().messages().size());
			assertTrue(negotiationService.view(a, inProgress).sentProposals().isEmpty());
		}
		finally
		{
			executor.shutdownNow();
		}
	}

	private MessageId send(UserId sender)
	{
		return conversationService.sendMessage(sender, conversation, 0, new MessageDraft(List.of(
				new ParagraphDraft(ParagraphType.SIMPLE, "Title", null, "Hello.", List.of(), List.of(), Optional.empty()))), NOON, null);
	}

	// Runs the action and returns what it threw, or null.
	private static Callable<Throwable> failureOf(Runnable action)
	{
		return () ->
		{
			try
			{
				action.run();
				return null;
			}
			catch (Throwable failure)
			{
				return failure;
			}
		};
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
