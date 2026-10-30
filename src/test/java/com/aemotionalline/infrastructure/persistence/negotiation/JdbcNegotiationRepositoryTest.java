package com.aemotionalline.infrastructure.persistence.negotiation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
import org.springframework.transaction.support.TransactionTemplate;

import com.aemotionalline.TestcontainersConfiguration;
import com.aemotionalline.domain.MutableClock;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.couple.CoupleRepository;
import com.aemotionalline.domain.negotiation.AgreementProposal;
import com.aemotionalline.domain.negotiation.AgreementText;
import com.aemotionalline.domain.negotiation.DiscussionProposal;
import com.aemotionalline.domain.negotiation.Negotiation;
import com.aemotionalline.domain.negotiation.NegotiationId;
import com.aemotionalline.domain.negotiation.NegotiationRepository;
import com.aemotionalline.domain.negotiation.NegotiationStatus;
import com.aemotionalline.domain.negotiation.ProposalId;
import com.aemotionalline.domain.negotiation.StoredFacts;
import com.aemotionalline.domain.user.UserId;

/**
 * Negotiations saved to MariaDB and loaded back are indistinguishable from the originals (same fingerprint as the domain's
 * restore tests) and go on working; a consumed one leaves its sent proposals behind; the row lock serialises two operations.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public class JdbcNegotiationRepositoryTest
{
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
	private UserId a;
	private UserId b;

	@BeforeEach
	void saveACouple()
	{
		couple = new Couple(CoupleId.random(), UserId.random(), UserId.random(), UserId.random());
		a = couple.getPartnerOneId();
		b = couple.getPartnerTwoId();
		couples.save(couple);
	}

	private Negotiation requested()
	{
		return Negotiation.start(NegotiationId.random(), couple, new AgreementProposal(ProposalId.random(), a, "TEXT"), clock);
	}

	private Negotiation saveAndReload(Negotiation negotiation)
	{
		negotiations.save(negotiation);
		Negotiation loaded = negotiations.findById(negotiation.getId()).orElseThrow();

		assertEquals(StoredFacts.fingerprint(negotiation), StoredFacts.fingerprint(loaded));

		return loaded;
	}

	@Test
	void shouldSaveAndReloadAWaitingRequest()
	{
		Negotiation loaded = saveAndReload(requested());

		loaded.acceptNegotiation(b);
		loaded.sendProposal(a, "JUSTIFICATION");
		assertEquals(1, loaded.getProposals().size());
	}

	@Test
	void shouldSaveAndReloadAnExchangeWithADraftInProgress()
	{
		Negotiation negotiation = requested();
		negotiation.acceptNegotiation(b);
		clock.advance(Duration.ofHours(1));
		negotiation.sendProposal(a, "MINE");
		negotiation.refuseProposal(b, ProposalId.random());
		negotiation.editCurrentProposal(b, new AgreementText("B'S DRAFT"));

		Negotiation loaded = saveAndReload(negotiation);

		loaded.sendProposal(b, "BETTER");
		loaded.acceptProposal(a);
		assertTrue(loaded.getLastProposal().isAccepted());
	}

	@Test
	void shouldSaveTheChangesOfALoadedNegotiation()
	{
		Negotiation negotiation = requested();
		negotiations.save(negotiation);

		Negotiation loaded = negotiations.findById(negotiation.getId()).orElseThrow();
		loaded.acceptNegotiation(b);
		loaded.sendProposal(a, "FIRST");
		loaded.refuseProposal(b, ProposalId.random());
		negotiations.save(loaded);

		Negotiation reloaded = negotiations.findById(negotiation.getId()).orElseThrow();
		assertEquals(StoredFacts.fingerprint(loaded), StoredFacts.fingerprint(reloaded));
	}

	@Test
	void shouldSaveAndReloadANegotiationOfADiscussionTitle()
	{
		Negotiation negotiation = Negotiation.start(NegotiationId.random(), couple, new DiscussionProposal(ProposalId.random(), a, "Holidays", "Where and when"), clock);
		negotiation.acceptNegotiation(b);
		negotiation.sendProposal(a, "LET'S TALK");

		saveAndReload(negotiation);
	}

	@Test
	void shouldFindOnlyTheFailedNegotiationsOfTheCoupleInTheOrderTheyStarted()
	{
		Negotiation refused = requested();
		refused.refuseNegotiation(b);
		clock.advance(Duration.ofDays(1));
		Negotiation abandoned = requested();
		abandoned.abandon(a);
		Negotiation open = requested();

		Couple other = new Couple(CoupleId.random(), UserId.random(), UserId.random(), UserId.random());
		couples.save(other);
		Negotiation othersFailure = Negotiation.start(NegotiationId.random(), other, new AgreementProposal(ProposalId.random(), other.getPartnerOneId(), "X"), clock);
		othersFailure.abandon(other.getPartnerOneId());

		List.of(abandoned, open, refused, othersFailure).forEach(negotiations::save);

		List<Negotiation> failed = negotiations.findFailedStartsOf(couple.getId());

		assertEquals(List.of(refused.getId(), abandoned.getId()), failed.stream().map(Negotiation::getId).toList());
		assertEquals(StoredFacts.fingerprint(abandoned), StoredFacts.fingerprint(failed.get(1)));
	}

	@Test
	void shouldDeleteTheHeaderAndTheDraftButKeepTheSentProposals()
	{
		Negotiation negotiation = requested();
		negotiation.acceptNegotiation(b);
		negotiation.sendProposal(a, "FIRST");
		negotiation.refuseProposal(b, ProposalId.random());
		negotiations.save(negotiation);

		negotiations.delete(negotiation.getId());

		assertTrue(negotiations.findById(negotiation.getId()).isEmpty());
		List<String> kept = jdbc.sql("SELECT status FROM proposal WHERE negotiation_id = ? ORDER BY position")
				.param(negotiation.getId().value())
				.query(String.class)
				.list();
		assertEquals(List.of("REFUSED"), kept);
	}

	@Test
	void shouldMakeASecondOperationWaitForTheFirstAndSeeItsResult() throws Exception
	{
		Negotiation negotiation = requested();
		negotiations.save(negotiation);

		CountDownLatch firstHasTheLock = new CountDownLatch(1);
		CountDownLatch releaseTheFirst = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);

		try
		{
			Future<?> first = executor.submit(() -> transactions.executeWithoutResult(status ->
			{
				Negotiation locked = negotiations.findByIdForUpdate(negotiation.getId()).orElseThrow();
				firstHasTheLock.countDown();
				await(releaseTheFirst);
				locked.abandon(a);
				negotiations.save(locked);
			}));

			assertTrue(firstHasTheLock.await(10, TimeUnit.SECONDS));

			Future<NegotiationStatus> second = executor.submit(() -> transactions.execute(status ->
					negotiations.findByIdForUpdate(negotiation.getId()).orElseThrow().getNegotiationStatus()));

			// While the first holds the lock, the second can't even load the negotiation.
			Thread.sleep(500);
			assertFalse(second.isDone());

			releaseTheFirst.countDown();
			first.get(10, TimeUnit.SECONDS);

			assertEquals(NegotiationStatus.ABANDONED, second.get(10, TimeUnit.SECONDS));
		}
		finally
		{
			executor.shutdownNow();
		}
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
