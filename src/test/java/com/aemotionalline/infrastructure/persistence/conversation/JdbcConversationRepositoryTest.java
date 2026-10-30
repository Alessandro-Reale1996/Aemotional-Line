package com.aemotionalline.infrastructure.persistence.conversation;

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
import java.util.UUID;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import com.aemotionalline.TestcontainersConfiguration;
import com.aemotionalline.domain.MutableClock;
import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.constraint.ConstraintContext;
import com.aemotionalline.domain.constraint.GeoPosition;
import com.aemotionalline.domain.conversation.Conversation;
import com.aemotionalline.domain.conversation.ConversationFacts;
import com.aemotionalline.domain.conversation.ConversationId;
import com.aemotionalline.domain.conversation.ConversationRepository;
import com.aemotionalline.domain.conversation.FailedNegotiationRecord;
import com.aemotionalline.domain.conversation.FullConversation;
import com.aemotionalline.domain.conversation.NegotiationRecord;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.couple.CoupleRepository;
import com.aemotionalline.domain.message.Message;
import com.aemotionalline.domain.message.MessageId;
import com.aemotionalline.domain.negotiation.AgreementProposal;
import com.aemotionalline.domain.negotiation.AgreementText;
import com.aemotionalline.domain.negotiation.Negotiation;
import com.aemotionalline.domain.negotiation.NegotiationId;
import com.aemotionalline.domain.negotiation.NegotiationRepository;
import com.aemotionalline.domain.negotiation.ProposalId;
import com.aemotionalline.domain.user.UserId;

/**
 * Conversations saved to MariaDB and loaded back are indistinguishable from the originals (same fingerprint as the domain's
 * restore tests); saving adds rows and never rewrites the stored ones; the whole flows that consume negotiations work in
 * one transaction; the row lock serialises two operations and the domain rules decide the second.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public class JdbcConversationRepositoryTest
{
	// B has a location constraint on writing (see FullConversation): B writes away from home.
	private static final ConstraintContext AWAY = new ConstraintContext(LocalTime.NOON, new GeoPosition(45.48, 9.19));

	@Autowired
	private ConversationRepository conversations;

	@Autowired
	private NegotiationRepository negotiations;

	@Autowired
	private CoupleRepository couples;

	@Autowired
	private TransactionTemplate transactions;

	@Autowired
	private JdbcClient jdbc;

	// Microseconds: what DATETIME(6) keeps.
	private final MutableClock clock = new MutableClock(Instant.parse("2026-10-30T10:00:00.123456Z"));

	private Couple couple;

	@BeforeEach
	void saveACouple()
	{
		couple = new Couple(CoupleId.random(), UserId.random(), UserId.random(), UserId.random());
		couples.save(couple);
	}

	private FullConversation savedFullConversation()
	{
		FullConversation full = new FullConversation(couple, ConversationId.random(), clock);
		conversations.save(full.conversation);

		return full;
	}

	private Conversation load(ConversationId id)
	{
		return conversations.findById(id).orElseThrow();
	}

	private static Message message(UserId sender, com.aemotionalline.domain.message.Paragraph... paragraphs)
	{
		Message message = new Message(MessageId.random(), sender);

		for (var paragraph : paragraphs)
		{
			message.addParagraph(paragraph);
		}

		return message;
	}

	private long count(String sql, Object... params)
	{
		return jdbc.sql(sql).params(params).query(Long.class).single();
	}

	@Test
	void shouldSaveAndReloadAConversationWithEveryKindOfState()
	{
		FullConversation full = savedFullConversation();

		Conversation loaded = load(full.conversation.getId());

		assertEquals(ConversationFacts.fingerprint(full.conversation), ConversationFacts.fingerprint(loaded));
	}

	@Test
	void shouldGoOnFromTheReloadedConversationAndAddOnlyNewRows()
	{
		FullConversation full = savedFullConversation();
		UUID id = full.conversation.getId().value();
		List<Map<String, Object>> storedMessages = jdbc.sql("SELECT id, sequence, sent_at FROM message WHERE conversation_id = ? ORDER BY sequence").param(id).query().listOfRows();
		long paragraphs = count("SELECT COUNT(*) FROM paragraph p JOIN message m ON m.id = p.message_id WHERE m.conversation_id = ?", id);

		Conversation loaded = load(full.conversation.getId());
		loaded.readMessage(full.b, full.main, full.lastMainMessage, FullConversation.NOON);
		loaded.sendMessage(message(full.b, FullConversation.simple(full.t), FullConversation.simple()), full.main, AWAY);
		conversations.save(loaded);
		// Saving again changes nothing.
		conversations.save(loaded);

		List<Map<String, Object>> after = jdbc.sql("SELECT id, sequence, sent_at FROM message WHERE conversation_id = ? ORDER BY sequence").param(id).query().listOfRows();
		assertEquals(storedMessages, after.subList(0, storedMessages.size()));
		assertEquals(storedMessages.size() + 1, after.size());
		assertEquals(paragraphs + 2, count("SELECT COUNT(*) FROM paragraph p JOIN message m ON m.id = p.message_id WHERE m.conversation_id = ?", id));
		assertEquals(1, count("SELECT COUNT(*) FROM message_read WHERE message_id = ?", full.lastMainMessage.value()));

		assertEquals(ConversationFacts.fingerprint(loaded), ConversationFacts.fingerprint(load(full.conversation.getId())));
	}

	@Test
	void shouldSaveTheStateChangesOfPendingRequests()
	{
		FullConversation full = savedFullConversation();

		Conversation loaded = load(full.conversation.getId());
		loaded.confirmTherapistClosing(full.pendingBranch, full.therapist);
		loaded.declareOutcome(full.pendingBranch, full.a, com.aemotionalline.domain.conversation.OutcomeDeclaration.Outcome.RESOLVED, "Fine by me.");
		loaded.approveConstraints(full.pendingConstraints, full.b);
		conversations.save(loaded);

		assertEquals(ConversationFacts.fingerprint(loaded), ConversationFacts.fingerprint(load(full.conversation.getId())));
	}

	@Test
	void shouldChangeTheAgreementAndConsumeTheNegotiationInOneTransaction()
	{
		FullConversation full = savedFullConversation();
		Negotiation change = Negotiation.start(NegotiationId.random(), couple, new AgreementProposal(ProposalId.random(), full.a, "THIRD AGREEMENT"), clock);
		change.acceptNegotiation(full.b);
		change.sendProposal(full.a, "LET'S CHANGE");
		change.acceptProposal(full.b);
		negotiations.save(change);

		transactions.executeWithoutResult(status ->
		{
			Conversation locked = conversations.findByIdForUpdate(full.conversation.getId()).orElseThrow();
			Negotiation negotiation = negotiations.findByIdForUpdate(change.getId()).orElseThrow();
			locked.modifyAgreement(negotiation);
			negotiations.delete(negotiation.getId());
			conversations.save(locked);
		});

		Conversation reloaded = load(full.conversation.getId());
		NegotiationRecord record = reloaded.getNegotiationHistory().getLast();
		assertEquals(change.getId(), record.negotiationId());
		assertEquals(new AgreementText("THIRD AGREEMENT"), reloaded.getAgreement().getContent());
		assertEquals("LET'S CHANGE", record.finalProposal().getJustification());
		assertTrue(negotiations.findById(change.getId()).isEmpty());
	}

	@Test
	void shouldKeepTheRequestOfAFailedNegotiationWhateverTheOrderOfDeleteAndSave()
	{
		FullConversation full = savedFullConversation();

		for (boolean deleteFirst : List.of(true, false))
		{
			// Accepted, then abandoned before anything was sent: its unsent draft has the request's id.
			Negotiation failed = Negotiation.start(NegotiationId.random(), couple, new AgreementProposal(ProposalId.random(), full.a, "MAYBE"), clock);
			failed.acceptNegotiation(full.b);
			failed.abandon(full.b);
			negotiations.save(failed);

			transactions.executeWithoutResult(status ->
			{
				Conversation locked = conversations.findByIdForUpdate(full.conversation.getId()).orElseThrow();
				Negotiation negotiation = negotiations.findByIdForUpdate(failed.getId()).orElseThrow();
				locked.recordFailedNegotiation(negotiation);

				if (deleteFirst)
				{
					negotiations.delete(negotiation.getId());
					conversations.save(locked);
				}
				else
				{
					conversations.save(locked);
					negotiations.delete(negotiation.getId());
				}
			});

			FailedNegotiationRecord record = load(full.conversation.getId()).getFailedNegotiations().getLast();
			assertEquals(failed.getId(), record.negotiationId());
			assertEquals(List.of(failed.getRequest().getId()), record.proposals().stream().map(proposal -> proposal.getId()).toList());
			assertEquals(new AgreementText("MAYBE"), record.proposals().getFirst().getContent());
			assertTrue(negotiations.findById(failed.getId()).isEmpty());
		}
	}

	@Test
	void shouldStartTheConversationWithTheCouplesFailedStartsAndRemoveThemFromTheNegotiations()
	{
		Negotiation refused = Negotiation.start(NegotiationId.random(), couple, new AgreementProposal(ProposalId.random(), couple.getPartnerOneId(), "TOO EARLY"), clock);
		refused.refuseNegotiation(couple.getPartnerTwoId());
		negotiations.save(refused);

		Negotiation start = Negotiation.start(NegotiationId.random(), couple, new AgreementProposal(ProposalId.random(), couple.getPartnerOneId(), "NOW"), clock);
		start.acceptNegotiation(couple.getPartnerTwoId());
		start.sendProposal(couple.getPartnerOneId(), "READY");
		start.acceptProposal(couple.getPartnerTwoId());
		negotiations.save(start);

		ConversationId id = ConversationId.random();

		transactions.executeWithoutResult(status ->
		{
			List<Negotiation> failures = negotiations.findFailedStartsOf(couple.getId());
			Negotiation agreed = negotiations.findByIdForUpdate(start.getId()).orElseThrow();
			Conversation conversation = Conversation.start(id, couple, agreed, Clock.systemUTC(), failures);
			conversations.save(conversation);
			negotiations.delete(agreed.getId());
			failures.forEach(failure -> negotiations.delete(failure.getId()));
		});

		Conversation loaded = conversations.findByCouple(couple.getId()).orElseThrow();
		assertEquals(id, loaded.getId());
		assertEquals(List.of(refused.getId()), loaded.getFailedNegotiations().stream().map(FailedNegotiationRecord::negotiationId).toList());
		assertEquals(NegotiationRecord.Purpose.STARTED_CONVERSATION, loaded.getFailedNegotiations().getFirst().attemptedPurpose());
		assertTrue(negotiations.findFailedStartsOf(couple.getId()).isEmpty());
	}

	@Test
	void shouldMakeASecondOperationWaitAndLetTheDomainRulesDecideIt() throws Exception
	{
		FullConversation full = savedFullConversation();
		ConversationId id = full.conversation.getId();
		CountDownLatch firstHasTheLock = new CountDownLatch(1);
		CountDownLatch releaseTheFirst = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);

		try
		{
			// The therapist closes the pending branch...
			Future<?> first = executor.submit(() -> transactions.executeWithoutResult(status ->
			{
				Conversation locked = conversations.findByIdForUpdate(id).orElseThrow();
				firstHasTheLock.countDown();
				await(releaseTheFirst);
				locked.confirmTherapistClosing(full.pendingBranch, full.therapist);
				conversations.save(locked);
			}));

			assertTrue(firstHasTheLock.await(10, TimeUnit.SECONDS));

			// ...while B tries to answer in it.
			Future<Throwable> second = executor.submit(() ->
			{
				try
				{
					transactions.executeWithoutResult(status ->
					{
						Conversation locked = conversations.findByIdForUpdate(id).orElseThrow();
						MessageId lastOfBranch = locked.findDiscussion(full.pendingBranch).getMessages().getLast().getId();
						locked.readMessage(full.b, full.pendingBranch, lastOfBranch, FullConversation.NOON);
						locked.sendMessage(message(full.b, FullConversation.simple()), full.pendingBranch, AWAY);
						conversations.save(locked);
					});
					return null;
				}
				catch (Throwable failure)
				{
					return failure;
				}
			});

			Thread.sleep(500);
			assertFalse(second.isDone());

			releaseTheFirst.countDown();
			first.get(10, TimeUnit.SECONDS);

			// The second loaded the closed branch: the domain rejected the message, nothing was written.
			assertTrue(second.get(10, TimeUnit.SECONDS) instanceof DomainException);
			assertEquals(1, load(id).findDiscussion(full.pendingBranch).getMessages().size());
			assertTrue(load(id).findDiscussion(full.pendingBranch).isClosed());
		}
		finally
		{
			executor.shutdownNow();
		}
	}

	@Test
	void shouldFindNothingForAnUnknownConversationOrCouple()
	{
		assertTrue(conversations.findById(ConversationId.random()).isEmpty());
		assertTrue(conversations.findByCouple(CoupleId.random()).isEmpty());
	}

	@Test
	void shouldRequireATransactionToLockAConversation()
	{
		assertThrows(IllegalTransactionStateException.class, () -> conversations.findByIdForUpdate(ConversationId.random()));
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
